package ai.flowguard.fixture.risk;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class RiskEvaluationService {

    private final JdbcTemplate jdbc;
    private final RiskUsageStore riskUsageStore;

    public RiskEvaluationService(JdbcTemplate jdbcTemplate, RiskUsageStore riskUsageStore) {
        this.jdbc = jdbcTemplate;
        this.riskUsageStore = riskUsageStore;
    }

    public CustomerRiskProfile loadProfile(UUID accountId) {
        return jdbc.query(
                """
                SELECT account_id, risk_tier, single_txn_limit_minor, daily_volume_limit_minor,
                       velocity_max_txn_per_hour, idempotency_ttl_seconds
                FROM customer_risk_profiles WHERE account_id = ?
                """,
                rs -> rs.next()
                        ? new CustomerRiskProfile(
                        (UUID) rs.getObject("account_id"),
                        rs.getString("risk_tier"),
                        rs.getLong("single_txn_limit_minor"),
                        rs.getLong("daily_volume_limit_minor"),
                        rs.getInt("velocity_max_txn_per_hour"),
                        rs.getInt("idempotency_ttl_seconds"))
                        : defaultProfile(accountId),
                accountId);
    }

    public RiskDecision evaluate(UUID accountId, long amountMinor, String operation) {
        CustomerRiskProfile profile = loadProfile(accountId);
        if (amountMinor > profile.singleTxnLimitMinor()) {
            return RiskDecision.deny(profile.riskTier(), "SINGLE_TXN_LIMIT",
                    "Amount exceeds single transaction limit for tier " + profile.riskTier());
        }
        long dailyUsed = riskUsageStore.dailyVolumeUsed(accountId);
        if (dailyUsed + amountMinor > profile.dailyVolumeLimitMinor()) {
            return RiskDecision.deny(profile.riskTier(), "DAILY_VOLUME_LIMIT",
                    "Daily volume limit exceeded for customer");
        }
        long hourlyCount = riskUsageStore.hourlyTxnCount(accountId);
        if (hourlyCount >= profile.velocityMaxTxnPerHour()) {
            return RiskDecision.deny(profile.riskTier(), "VELOCITY_LIMIT",
                    "Too many transactions in the current hour");
        }
        return RiskDecision.allow(profile.riskTier());
    }

    public void recordUsage(UUID accountId, long amountMinor) {
        riskUsageStore.addDailyVolume(accountId, amountMinor);
        riskUsageStore.incrementHourlyTxn(accountId);
    }

    public Map<String, Object> usageSnapshot(UUID accountId) {
        CustomerRiskProfile profile = loadProfile(accountId);
        return Map.of(
                "accountId", accountId.toString(),
                "riskTier", profile.riskTier(),
                "dailyVolumeUsedMinor", riskUsageStore.dailyVolumeUsed(accountId),
                "dailyVolumeLimitMinor", profile.dailyVolumeLimitMinor(),
                "hourlyTxnCount", riskUsageStore.hourlyTxnCount(accountId),
                "velocityMaxTxnPerHour", profile.velocityMaxTxnPerHour());
    }

    private static CustomerRiskProfile defaultProfile(UUID accountId) {
        return new CustomerRiskProfile(accountId, "STANDARD", 500_000L, 2_000_000L, 60, 86_400);
    }
}
