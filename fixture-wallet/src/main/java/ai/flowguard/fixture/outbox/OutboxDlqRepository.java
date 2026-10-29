package ai.flowguard.fixture.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OutboxDlqRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxDlqRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listSagaDlq(int limit) {
        return jdbc.queryForList("""
                SELECT id, idempotency_key, payload_json, publish_attempts, last_error, dead_at
                FROM saga_event_outbox_dlq
                ORDER BY dead_at DESC
                LIMIT ?
                """, limit);
    }

    public List<Map<String, Object>> listTransactionDlq(int limit) {
        return jdbc.queryForList("""
                SELECT id, partition_key, payload_json, publish_attempts, last_error, dead_at
                FROM transaction_event_outbox_dlq
                ORDER BY dead_at DESC
                LIMIT ?
                """, limit);
    }

    public Optional<Map<String, Object>> findSagaDlq(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, idempotency_key, payload_json, publish_attempts, last_error
                FROM saga_event_outbox_dlq WHERE id = ?
                """, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<Map<String, Object>> findTransactionDlq(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, partition_key, payload_json, publish_attempts, last_error
                FROM transaction_event_outbox_dlq WHERE id = ?
                """, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public UUID requeueSagaFromDlq(UUID dlqId) {
        Map<String, Object> row = findSagaDlq(dlqId).orElseThrow(() -> new IllegalArgumentException("Saga DLQ row not found"));
        UUID newId = UUID.randomUUID();
        String idempotencyKey = (String) row.get("idempotency_key");
        jdbc.update("""
                INSERT INTO saga_event_outbox (id, idempotency_key, payload_json, publish_attempts)
                VALUES (?, ?, ?::jsonb, 0)
                """, newId, idempotencyKey, payloadJson(row.get("payload_json")));
        jdbc.update("DELETE FROM saga_event_outbox_dlq WHERE id = ?", dlqId);
        return newId;
    }

    public UUID requeueTransactionFromDlq(UUID dlqId) {
        Map<String, Object> row = findTransactionDlq(dlqId).orElseThrow(() -> new IllegalArgumentException("Transaction DLQ row not found"));
        UUID newId = UUID.randomUUID();
        String partitionKey = (String) row.get("partition_key");
        jdbc.update("""
                INSERT INTO transaction_event_outbox (id, partition_key, payload_json, publish_attempts)
                VALUES (?, ?, ?::jsonb, 0)
                """, newId, partitionKey, payloadJson(row.get("payload_json")));
        jdbc.update("DELETE FROM transaction_event_outbox_dlq WHERE id = ?", dlqId);
        return newId;
    }

    public int countSagaDlq() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*)::int FROM saga_event_outbox_dlq", Integer.class);
        return count == null ? 0 : count;
    }

    public int countTransactionDlq() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*)::int FROM transaction_event_outbox_dlq", Integer.class);
        return count == null ? 0 : count;
    }

    private String payloadJson(Object raw) {
        if (raw == null) {
            return "{}";
        }
        if (raw instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            return raw.toString();
        }
    }
}
