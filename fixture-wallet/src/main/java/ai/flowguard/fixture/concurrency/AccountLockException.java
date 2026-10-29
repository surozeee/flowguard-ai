package ai.flowguard.fixture.concurrency;

public class AccountLockException extends RuntimeException {

    public AccountLockException(String message) {
        super(message);
    }
}
