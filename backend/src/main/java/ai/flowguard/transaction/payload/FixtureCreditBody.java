package ai.flowguard.transaction.payload;

import java.util.UUID;

public record FixtureCreditBody(
        String clientReference,
        String idempotencyKey,
        UUID targetAccountId,
        long amountMinor
) {}
