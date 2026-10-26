package ai.flowguard.fixture.idempotency;

public class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException(String idempotencyKey) {
        super("Payment with idempotency key already in progress: " + idempotencyKey);
    }
}
