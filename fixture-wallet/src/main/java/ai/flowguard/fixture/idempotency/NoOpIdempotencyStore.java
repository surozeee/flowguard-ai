package ai.flowguard.fixture.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.redis-idempotency-enabled", havingValue = "false")
public class NoOpIdempotencyStore implements IdempotencyStore {

    @Override
    public Optional<UUID> findPaymentId(String idempotencyKey) {
        return Optional.empty();
    }

    @Override
    public IdempotencyClaimResult tryClaim(String idempotencyKey, int ttlSeconds) {
        return IdempotencyClaimResult.granted(UUID.randomUUID().toString());
    }

    @Override
    public void confirm(String idempotencyKey, UUID accountId, UUID paymentId, int ttlSeconds) {
        // no-op
    }

    @Override
    public void release(String idempotencyKey) {
        // no-op
    }

    @Override
    public void clearKey(String idempotencyKey) {
        // no-op
    }
}
