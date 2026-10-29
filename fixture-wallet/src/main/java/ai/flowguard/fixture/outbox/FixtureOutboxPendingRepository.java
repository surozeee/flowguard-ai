package ai.flowguard.fixture.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class FixtureOutboxPendingRepository {

    private final JdbcTemplate jdbc;

    public FixtureOutboxPendingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public int countSagaPending() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM saga_event_outbox WHERE published_at IS NULL", Integer.class);
        return count == null ? 0 : count;
    }

    public int countTransactionPending() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM transaction_event_outbox WHERE published_at IS NULL", Integer.class);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> listSagaPending(int limit) {
        return jdbc.queryForList("""
                SELECT id, idempotency_key, publish_attempts, last_error, created_at
                FROM saga_event_outbox
                WHERE published_at IS NULL
                ORDER BY created_at ASC
                LIMIT ?
                """, limit);
    }

    public List<Map<String, Object>> listTransactionPending(int limit) {
        return jdbc.queryForList("""
                SELECT id, partition_key, publish_attempts, last_error, created_at
                FROM transaction_event_outbox
                WHERE published_at IS NULL
                ORDER BY created_at ASC
                LIMIT ?
                """, limit);
    }
}
