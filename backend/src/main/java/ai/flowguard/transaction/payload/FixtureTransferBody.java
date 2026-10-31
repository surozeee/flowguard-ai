package ai.flowguard.transaction.payload;

import java.util.UUID;

public record FixtureTransferBody(
        UUID sourceAccountId,
        UUID targetAccountId,
        long amountMinor,
        String idempotencyKey
) {}
