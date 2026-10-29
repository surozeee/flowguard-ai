package ai.flowguard.fixture.saga;

public enum PaymentSagaState {
    STARTED,
    RISK_APPROVED,
    IDEMPOTENCY_CLAIMED,
    LEDGER_COMMITTED,
    EVENT_PUBLISHED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
