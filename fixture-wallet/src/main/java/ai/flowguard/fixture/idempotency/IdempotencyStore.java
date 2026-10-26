package ai.flowguard.fixture.idempotency;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStore {

    Optional<UUID> findPaymentId(String idempotencyKey);

    IdempotencyClaimResult tryClaim(String idempotencyKey, int ttlSeconds);

    void confirm(String idempotencyKey, UUID accountId, UUID paymentId, int ttlSeconds);

    void release(String idempotencyKey);

    void clearKey(String idempotencyKey);
}
