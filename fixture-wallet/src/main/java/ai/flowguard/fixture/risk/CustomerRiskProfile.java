package ai.flowguard.fixture.risk;

import java.util.UUID;

public record CustomerRiskProfile(
        UUID accountId,
        String riskTier,
        long singleTxnLimitMinor,
        long dailyVolumeLimitMinor,
        int velocityMaxTxnPerHour,
        int idempotencyTtlSeconds
) {
}
