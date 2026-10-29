package ai.flowguard.fixture.saga;

public final class OutboxPublishSupport {

    private final int maxAttempts;

    public OutboxPublishSupport(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean exhausted(int attemptsAfterFailure) {
        return attemptsAfterFailure >= maxAttempts;
    }
}
