package ai.flowguard.fixture.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowguard.fixture")
public record FixtureProperties(
        String variant,
        Boolean messagingEnabled,
        Boolean redisIdempotencyEnabled,
        String transactionsTopic,
        String sagaEventsTopic,
        int staleSagaMinutes,
        long sagaOutboxPollMs,
        long transactionOutboxPollMs,
        int maxOutboxPublishAttempts
) {
    public FixtureProperties {
        if (messagingEnabled == null) {
            messagingEnabled = true;
        }
        if (redisIdempotencyEnabled == null) {
            redisIdempotencyEnabled = true;
        }
        if (transactionsTopic == null || transactionsTopic.isBlank()) {
            transactionsTopic = "flowguard.transactions";
        }
        if (sagaEventsTopic == null || sagaEventsTopic.isBlank()) {
            sagaEventsTopic = "flowguard.saga.events";
        }
        if (variant == null || variant.isBlank()) {
            variant = "dup-credit-vulnerable";
        }
        if (staleSagaMinutes <= 0) {
            staleSagaMinutes = 5;
        }
        if (sagaOutboxPollMs <= 0) {
            sagaOutboxPollMs = 500;
        }
        if (transactionOutboxPollMs <= 0) {
            transactionOutboxPollMs = 500;
        }
        if (maxOutboxPublishAttempts <= 0) {
            maxOutboxPublishAttempts = 5;
        }
    }

    public boolean usesRedisIdempotencyLock() {
        return redisIdempotencyEnabled && !"dup-credit-vulnerable".equals(variant);
    }
}
