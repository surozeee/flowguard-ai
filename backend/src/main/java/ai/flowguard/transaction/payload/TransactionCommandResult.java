package ai.flowguard.transaction.payload;

import java.util.UUID;

public record TransactionCommandResult(
        TransactionKind kind,
        String idempotencyKey,
        UUID sourceAccountId,
        UUID targetAccountId,
        long amountMinor,
        FixturePaymentAck fixture,
        TransactionIntegrationView integration
) {}
