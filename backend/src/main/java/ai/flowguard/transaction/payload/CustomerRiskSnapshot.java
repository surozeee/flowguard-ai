package ai.flowguard.transaction.payload;

public record CustomerRiskSnapshot(
        String accountId,
        String riskTier,
        long settledTxnCount
) {}
