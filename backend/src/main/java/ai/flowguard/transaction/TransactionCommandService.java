package ai.flowguard.transaction;

import ai.flowguard.config.FlowGuardProperties;
import ai.flowguard.ops.FixtureOpsClient;
import ai.flowguard.saga.SagaChoreographyService;
import ai.flowguard.saga.SagaTraceVerifier;
import ai.flowguard.transaction.payload.FixtureCreditBody;
import ai.flowguard.transaction.payload.FixturePaymentAck;
import ai.flowguard.transaction.payload.FixtureTransferBody;
import ai.flowguard.transaction.payload.OrchestratorSagaRow;
import ai.flowguard.transaction.payload.TransactionCommandResult;
import ai.flowguard.transaction.payload.TransactionIntegrationView;
import ai.flowguard.transaction.payload.TransactionKind;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

@Service
public class TransactionCommandService {

    private static final UUID TREASURY = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final RestClient restClient;
    private final FlowGuardProperties properties;
    private final FixtureOpsClient fixtureOpsClient;
    private final SagaChoreographyService sagaChoreographyService;
    private final SagaTraceVerifier sagaTraceVerifier;
    private final TransactionRiskCacheService riskCacheService;

    public TransactionCommandService(
            RestClient restClient,
            FlowGuardProperties properties,
            FixtureOpsClient fixtureOpsClient,
            SagaChoreographyService sagaChoreographyService,
            SagaTraceVerifier sagaTraceVerifier,
            TransactionRiskCacheService riskCacheService) {
        this.restClient = restClient;
        this.properties = properties;
        this.fixtureOpsClient = fixtureOpsClient;
        this.sagaChoreographyService = sagaChoreographyService;
        this.sagaTraceVerifier = sagaTraceVerifier;
        this.riskCacheService = riskCacheService;
    }

    public TransactionCommandResult credit(CreditCommand command) {
        String idempotencyKey = requireKey(command.idempotencyKey());
        UUID target = command.targetAccountId() != null
                ? command.targetAccountId()
                : UUID.fromString(properties.fixture().customerAccountId());
        String clientReference = command.clientReference() == null || command.clientReference().isBlank()
                ? "ui-credit-" + idempotencyKey
                : command.clientReference();
        FixturePaymentAck ack = post(
                properties.fixture().baseUrl() + "/payments",
                new FixtureCreditBody(clientReference, idempotencyKey, target, command.amountMinor()),
                idempotencyKey);
        return new TransactionCommandResult(
                TransactionKind.CREDIT,
                idempotencyKey,
                null,
                target,
                command.amountMinor(),
                ack,
                integration(idempotencyKey, target.toString()));
    }

    public TransactionCommandResult transfer(TransferCommand command) {
        String idempotencyKey = requireKey(command.idempotencyKey());
        UUID source = command.sourceAccountId() != null
                ? command.sourceAccountId()
                : UUID.fromString(properties.fixture().customerAccountId());
        UUID target = command.targetAccountId() != null ? command.targetAccountId() : TREASURY;
        if (command.prefund()) {
            long fund = command.fundMinor() > 0 ? command.fundMinor() : Math.max(command.amountMinor() * 2, 100_000L);
            credit(new CreditCommand("prefund-" + idempotencyKey, "prefund-" + UUID.randomUUID(), source, fund));
        }
        FixturePaymentAck ack = post(
                properties.fixture().baseUrl() + "/transfers",
                new FixtureTransferBody(source, target, command.amountMinor(), idempotencyKey),
                idempotencyKey);
        return new TransactionCommandResult(
                TransactionKind.TRANSFER,
                idempotencyKey,
                source,
                target,
                command.amountMinor(),
                ack,
                integration(idempotencyKey, source.toString()));
    }

    public TransactionIntegrationView integration(String idempotencyKey, String accountId) {
        String account = accountId == null || accountId.isBlank()
                ? properties.fixture().customerAccountId()
                : accountId;
        List<OrchestratorSagaRow> orchestrator = List.of();
        String orchestratorError = null;
        try {
            orchestrator = fixtureOpsClient.sagaRowsByIdempotency(idempotencyKey).stream()
                    .map(OrchestratorSagaRow::from)
                    .filter(row -> row != null)
                    .toList();
        } catch (Exception ex) {
            orchestratorError = ex.getMessage();
        }
        return new TransactionIntegrationView(
                idempotencyKey,
                account,
                riskCacheService.getIdempotencyRecord(idempotencyKey).orElse(null),
                riskCacheService.getLastTransaction(account).orElse(null),
                riskCacheService.getRiskSnapshot(account),
                orchestrator,
                orchestratorError,
                sagaChoreographyService.traceByIdempotencyKey(idempotencyKey),
                sagaTraceVerifier.verify(idempotencyKey));
    }

    private FixturePaymentAck post(String uri, Object body, String idempotencyKey) {
        try {
            FixturePaymentAck ack = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .body(FixturePaymentAck.class);
            return ack == null ? new FixturePaymentAck(null) : ack;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Fixture " + uri + " failed (" + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString(),
                    ex);
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        return key.trim();
    }

    public record CreditCommand(String clientReference, String idempotencyKey, UUID targetAccountId, long amountMinor) {}

    public record TransferCommand(
            UUID sourceAccountId,
            UUID targetAccountId,
            long amountMinor,
            String idempotencyKey,
            boolean prefund,
            long fundMinor) {}
}
