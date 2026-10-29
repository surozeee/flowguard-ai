package ai.flowguard.fixture.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class TransactionEventOutboxRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TransactionEventOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueue(UUID id, String partitionKey, Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO transaction_event_outbox (id, partition_key, payload_json)
                VALUES (?, ?, ?::jsonb)
                """, id, partitionKey, writeJson(payload));
    }

    public List<Map<String, Object>> findPending(int limit) {
        return jdbc.queryForList("""
                SELECT id, partition_key, payload_json, publish_attempts
                FROM transaction_event_outbox
                WHERE published_at IS NULL
                ORDER BY created_at ASC
                LIMIT ?
                """, limit);
    }

    public void markPublished(UUID id) {
        jdbc.update("UPDATE transaction_event_outbox SET published_at = now() WHERE id = ?", id);
    }

    public void recordFailure(UUID id, int attempts, String error) {
        jdbc.update("""
                UPDATE transaction_event_outbox SET publish_attempts = ?, last_error = ? WHERE id = ?
                """, attempts, error, id);
    }

    public void moveToDlq(UUID id, String partitionKey, Map<String, Object> payload, int attempts, String error) {
        jdbc.update("""
                INSERT INTO transaction_event_outbox_dlq (id, partition_key, payload_json, publish_attempts, last_error)
                VALUES (?, ?, ?::jsonb, ?, ?)
                """, id, partitionKey, writeJson(payload), attempts, error);
        jdbc.update("DELETE FROM transaction_event_outbox WHERE id = ?", id);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
