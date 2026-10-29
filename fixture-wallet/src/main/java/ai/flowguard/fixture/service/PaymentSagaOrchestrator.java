package ai.flowguard.fixture.service;

import ai.flowguard.fixture.concurrency.AccountLockService;
import ai.flowguard.fixture.config.FixtureProperties;
import ai.flowguard.fixture.idempotency.IdempotencyClaimResult;
import ai.flowguard.fixture.idempotency.IdempotencyInProgressException;
import ai.flowguard.fixture.idempotency.IdempotencyStore;
import ai.flowguard.fixture.messaging.TransactionEventPublisher;
import ai.flowguard.fixture.risk.CustomerRiskProfile;
import ai.flowguard.fixture.risk.RiskDecision;
import ai.flowguard.fixture.risk.RiskDeniedException;
import ai.flowguard.fixture.risk.RiskEvaluationService;
import ai.flowguard.fixture.saga.PaymentSagaMetrics;
import ai.flowguard.fixture.saga.PaymentSagaRepository;
import ai.flowguard.fixture.saga.PaymentSagaState;
import ai.flowguard.fixture.saga.SagaEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentSagaOrchestrator {

    private final FixtureProperties properties;
    private final IdempotencyStore idempotencyStore;
    private final RiskEvaluationService riskEvaluationService;
    private final TransactionEventPublisher transactionEventPublisher;
    private final SagaEventPublisher sagaEventPublisher;
    private final PaymentSagaRepository paymentSagaRepository;
    private final AccountLockService accountLockService;
    private final PaymentLedgerService paymentLedgerService;
    private final PaymentSagaMetrics paymentSagaMetrics;

    public PaymentSagaOrchestrator(
            FixtureProperties properties,
            IdempotencyStore idempotencyStore,
            RiskEvaluationService riskEvaluationService,
            TransactionEventPublisher transactionEventPublisher,
            SagaEventPublisher sagaEventPublisher,
            PaymentSagaRepository paymentSagaRepository,
            AccountLockService accountLockService,
            PaymentLedgerService paymentLedgerService,
            PaymentSagaMetrics paymentSagaMetrics) {
        this.properties = properties;
        this.idempotencyStore = idempotencyStore;
        this.riskEvaluationService = riskEvaluationService;
        this.transactionEventPublisher = transactionEventPublisher;
        this.sagaEventPublisher = sagaEventPublisher;
        this.paymentSagaRepository = paymentSagaRepository;
        this.accountLockService = accountLockService;
        this.paymentLedgerService = paymentLedgerService;
        this.paymentSagaMetrics = paymentSagaMetrics;
    }

    public UUID runCredit(String clientReference, String idempotencyKey, UUID targetAccountId, long amountMinor) {
        return run("CREDIT", idempotencyKey, targetAccountId, amountMinor, () -> paymentLedgerService.credit(
                clientReference, idempotencyKey, targetAccountId, amountMinor));
    }

    public UUID runDebit(UUID sourceAccountId, UUID targetAccountId, long amountMinor, String idempotencyKey) {
        return run("DEBIT", idempotencyKey, sourceAccountId, amountMinor, () -> paymentLedgerService.debit(
                sourceAccountId, targetAccountId, amountMinor, idempotencyKey));
    }

    private UUID run(
            String direction,
            String idempotencyKey,
            UUID accountId,
            long amountMinor,
            LedgerAction ledgerAction) {
        CustomerRiskProfile profile = riskEvaluationService.loadProfile(accountId);
        RiskDecision risk = riskEvaluationService.evaluate(accountId, amountMinor, direction);
        if (!risk.allowed()) {
            UUID sagaId = UUID.randomUUID();
            paymentSagaRepository.insert(sagaId, idempotencyKey, direction, accountId, Map.of(
                    "amountMinor", amountMinor, "direction", direction));
            publishRiskDenied(accountId, idempotencyKey, amountMinor, direction, risk);
            paymentSagaRepository.fail(sagaId, risk.message());
            publishSaga(sagaId, idempotencyKey, "SAGA_FAILED", PaymentSagaState.FAILED, "RISK_CHECK", accountId, null);
            throw new RiskDeniedException(risk.code(), risk.message());
        }

        if (properties.usesRedisIdempotencyLock()) {
            IdempotencyClaimResult claim = idempotencyStore.tryClaim(idempotencyKey, profile.idempotencyTtlSeconds());
            if (claim.status() == IdempotencyClaimResult.IdempotencyClaimStatus.REPLAY) {
                UUID paymentId = claim.paymentId().orElseThrow();
                UUID sagaId = UUID.randomUUID();
                paymentSagaRepository.insertCompletedReplay(
                        sagaId, idempotencyKey, direction, accountId, paymentId, Map.of(
                                "amountMinor", amountMinor, "direction", direction));
                publishSaga(sagaId, idempotencyKey, "SAGA_IDEMPOTENT_REPLAY", PaymentSagaState.COMPLETED, "REPLAY", accountId, paymentId);
                publishReplay(accountId, idempotencyKey, amountMinor, direction, profile, paymentId);
                paymentSagaMetrics.recordReplay();
                return paymentId;
            }
            if (claim.status() == IdempotencyClaimResult.IdempotencyClaimStatus.IN_PROGRESS) {
                paymentSagaMetrics.recordInProgressRejected();
                throw new IdempotencyInProgressException(idempotencyKey);
            }
        }

        UUID sagaId = UUID.randomUUID();
        paymentSagaRepository.insert(sagaId, idempotencyKey, direction, accountId, Map.of(
                "amountMinor", amountMinor, "direction", direction));
        publishSaga(sagaId, idempotencyKey, "SAGA_STARTED", PaymentSagaState.STARTED, "START", accountId, null);
        paymentSagaRepository.advance(sagaId, PaymentSagaState.RISK_APPROVED, "RISK_CHECK", null);
        publishSaga(sagaId, idempotencyKey, "SAGA_STEP_RISK_APPROVED", PaymentSagaState.RISK_APPROVED, "RISK_CHECK", accountId, null);

        if (properties.usesRedisIdempotencyLock()) {
            paymentSagaRepository.advance(sagaId, PaymentSagaState.IDEMPOTENCY_CLAIMED, "IDEMPOTENCY_CLAIM", null);
            publishSaga(sagaId, idempotencyKey, "SAGA_STEP_IDEMPOTENCY_CLAIMED", PaymentSagaState.IDEMPOTENCY_CLAIMED,
                    "IDEMPOTENCY_CLAIM", accountId, null);
        }

        UUID paymentId = null;
        try (AccountLockService.AccountLock ignored = accountLockService.acquire(accountId)) {
            paymentId = ledgerAction.execute();
            paymentSagaRepository.advance(sagaId, PaymentSagaState.LEDGER_COMMITTED, "LEDGER_COMMIT", paymentId);
            publishSaga(sagaId, idempotencyKey, "SAGA_STEP_LEDGER_COMMITTED", PaymentSagaState.LEDGER_COMMITTED,
                    "LEDGER_COMMIT", accountId, paymentId);

            if (properties.usesRedisIdempotencyLock()) {
                idempotencyStore.confirm(idempotencyKey, accountId, paymentId, profile.idempotencyTtlSeconds());
            }
            riskEvaluationService.recordUsage(accountId, amountMinor);
            transactionEventPublisher.publish(settledEvent(
                    accountId, idempotencyKey, amountMinor, direction, profile, risk, paymentId, properties.variant()));

            paymentSagaRepository.advance(sagaId, PaymentSagaState.EVENT_PUBLISHED, "EVENT_PUBLISH", paymentId);
            publishSaga(sagaId, idempotencyKey, "SAGA_STEP_EVENT_PUBLISHED", PaymentSagaState.EVENT_PUBLISHED,
                    "EVENT_PUBLISH", accountId, paymentId);

            paymentSagaRepository.advance(sagaId, PaymentSagaState.COMPLETED, "COMPLETE", paymentId);
            publishSaga(sagaId, idempotencyKey, "SAGA_COMPLETED", PaymentSagaState.COMPLETED, "COMPLETE", accountId, paymentId);
            paymentSagaMetrics.recordCompleted();
            return paymentId;
        } catch (RuntimeException ex) {
            compensate(sagaId, idempotencyKey, accountId, paymentId, ex.getMessage());
            throw ex;
        }
    }

    private void compensate(UUID sagaId, String idempotencyKey, UUID accountId, UUID paymentId, String reason) {
        if (paymentId != null) {
            paymentLedgerService.reverseIfSettled(paymentId);
        }
        paymentSagaRepository.advance(sagaId, PaymentSagaState.COMPENSATING, "COMPENSATE", paymentId);
        publishSaga(sagaId, idempotencyKey, "SAGA_COMPENSATING", PaymentSagaState.COMPENSATING, "COMPENSATE", accountId, null);
        if (properties.usesRedisIdempotencyLock()) {
            idempotencyStore.release(idempotencyKey);
        }
        paymentSagaRepository.advance(sagaId, PaymentSagaState.COMPENSATED, "COMPENSATED", null);
        publishSaga(sagaId, idempotencyKey, "SAGA_COMPENSATED", PaymentSagaState.COMPENSATED, "COMPENSATED", accountId, null);
        paymentSagaRepository.fail(sagaId, reason);
        paymentSagaMetrics.recordCompensated();
    }

    private void publishSaga(
            UUID sagaId,
            String idempotencyKey,
            String eventType,
            PaymentSagaState state,
            String step,
            UUID accountId,
            UUID paymentId) {
        sagaEventPublisher.publish(Map.of(
                "eventType", eventType,
                "pattern", "CHOREOGRAPHY",
                "sagaId", sagaId.toString(),
                "idempotencyKey", idempotencyKey,
                "sagaState", state.name(),
                "step", step,
                "accountId", accountId.toString(),
                "paymentId", paymentId == null ? "" : paymentId.toString(),
                "timestamp", Instant.now().toString()));
    }

    private void publishReplay(UUID accountId, String idempotencyKey, long amountMinor, String direction,
                               CustomerRiskProfile profile, UUID paymentId) {
        transactionEventPublisher.publish(Map.of(
                "eventType", "PAYMENT_IDEMPOTENT_REPLAY",
                "paymentId", paymentId.toString(),
                "accountId", accountId.toString(),
                "idempotencyKey", idempotencyKey,
                "amountMinor", amountMinor,
                "direction", direction,
                "riskTier", profile.riskTier(),
                "variant", properties.variant(),
                "timestamp", Instant.now().toString()));
    }

    private void publishRiskDenied(UUID accountId, String idempotencyKey, long amountMinor, String direction, RiskDecision risk) {
        transactionEventPublisher.publish(Map.of(
                "eventType", "PAYMENT_RISK_DENIED",
                "accountId", accountId.toString(),
                "idempotencyKey", idempotencyKey,
                "amountMinor", amountMinor,
                "direction", direction,
                "riskTier", risk.riskTier(),
                "riskDecision", risk.code(),
                "message", risk.message(),
                "variant", properties.variant(),
                "timestamp", Instant.now().toString()));
    }

    private static Map<String, Object> settledEvent(
            UUID accountId,
            String idempotencyKey,
            long amountMinor,
            String direction,
            CustomerRiskProfile profile,
            RiskDecision risk,
            UUID paymentId,
            String variant) {
        return Map.of(
                "eventType", "PAYMENT_SETTLED",
                "paymentId", paymentId.toString(),
                "accountId", accountId.toString(),
                "idempotencyKey", idempotencyKey,
                "amountMinor", amountMinor,
                "direction", direction,
                "riskTier", profile.riskTier(),
                "riskDecision", risk.code(),
                "variant", variant,
                "timestamp", Instant.now().toString());
    }

    @FunctionalInterface
    interface LedgerAction {
        UUID execute();
    }
}
