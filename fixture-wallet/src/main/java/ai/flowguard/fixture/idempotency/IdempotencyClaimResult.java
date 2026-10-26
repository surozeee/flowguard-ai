package ai.flowguard.fixture.idempotency;

import java.util.Optional;
import java.util.UUID;

public record IdempotencyClaimResult(
        IdempotencyClaimStatus status,
        Optional<UUID> paymentId,
        Optional<String> pendingToken
) {
    public static IdempotencyClaimResult replay(UUID paymentId) {
        return new IdempotencyClaimResult(IdempotencyClaimStatus.REPLAY, Optional.of(paymentId), Optional.empty());
    }

    public static IdempotencyClaimResult granted(String pendingToken) {
        return new IdempotencyClaimResult(IdempotencyClaimStatus.GRANTED, Optional.empty(), Optional.of(pendingToken));
    }

    public static IdempotencyClaimResult inProgress() {
        return new IdempotencyClaimResult(IdempotencyClaimStatus.IN_PROGRESS, Optional.empty(), Optional.empty());
    }

    public enum IdempotencyClaimStatus {
        REPLAY,
        GRANTED,
        IN_PROGRESS
    }
}
