package ai.flowguard.fixture.service;

import ai.flowguard.fixture.race.RaceCheckpoint;
import ai.flowguard.fixture.risk.RiskUsageStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FixtureResetService {

    private static final List<String> REDIS_CACHE_PATTERNS = List.of(
            "customer:txn:*",
            "customer:txn:settled-idem:*",
            "customer:risk:profile:*",
            "idem:global:*",
            "idem:payment:*",
            "idem:investigation:start:*",
            "evidence:*",
            "account:lock:*");

    private final JdbcTemplate jdbc;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final RiskUsageStore riskUsageStore;

    public FixtureResetService(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redis,
            RiskUsageStore riskUsageStore) {
        this.jdbc = jdbcTemplate;
        this.redis = redis;
        this.riskUsageStore = riskUsageStore;
    }

    @Transactional
    public void resetToSeed() {
        jdbc.update("DELETE FROM journal_lines");
        jdbc.update("DELETE FROM journals");
        jdbc.update("DELETE FROM saga_event_outbox_dlq");
        jdbc.update("DELETE FROM transaction_event_outbox_dlq");
        jdbc.update("DELETE FROM transaction_event_outbox");
        jdbc.update("DELETE FROM saga_event_outbox");
        jdbc.update("DELETE FROM payment_sagas");
        jdbc.update("DELETE FROM payment_idempotency_audit");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM customer_risk_profiles");
        jdbc.update("DELETE FROM accounts");

        UUID treasury = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID customer = UUID.fromString("00000000-0000-4000-8000-000000000002");

        jdbc.update("""
                INSERT INTO accounts (id, currency, opening_balance_minor, current_balance_minor, overdraft_allowed, row_version)
                VALUES (?, 'USD', 0, 0, false, 0),
                       (?, 'USD', 0, 0, false, 0)
                """, treasury, customer);

        jdbc.update("""
                INSERT INTO customer_risk_profiles (account_id, risk_tier, single_txn_limit_minor,
                daily_volume_limit_minor, velocity_max_txn_per_hour, idempotency_ttl_seconds)
                VALUES (?, 'STANDARD', 500000, 2000000, 60, 86400)
                """, customer);
        RaceCheckpoint.reset();
        riskUsageStore.clearAll();
        clearSharedCaches();
    }

    private void clearSharedCaches() {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return;
        }
        for (String pattern : REDIS_CACHE_PATTERNS) {
            Set<String> keys = template.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                template.delete(keys);
            }
        }
    }
}
