package ai.flowguard.fixture.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SagaEventOutboxRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SagaEventOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueue(UUID id, String idempotencyKey, Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO saga_event_outbox (id, idempotency_key, payload_json)
                VALUES (?, ?, ?::jsonb)
                """, id, idempotencyKey, writeJson(payload));
    }

    public List<Map<String, Object>> findPending(int limit) {
        return jdbc.queryForList("""
                SELECT id, idempotency_key, payload_json, publish_attempts
                FROM saga_event_outbox
                WHERE published_at IS NULL
                ORDER BY created_at ASC
                LIMIT ?
                """, limit);
    }

    public void markPublished(UUID id) {
        jdbc.update("UPDATE saga_event_outbox SET published_at = now() WHERE id = ?", id);
    }

    public void recordFailure(UUID id, int attempts, String error) {
        jdbc.update("""
                UPDATE saga_event_outbox SET publish_attempts = ?, last_error = ? WHERE id = ?
                """, attempts, error, id);
    }

    public void moveToDlq(UUID id, String idempotencyKey, Map<String, Object> payload, int attempts, String error) {
        jdbc.update("""
                INSERT INTO saga_event_outbox_dlq (id, idempotency_key, payload_json, publish_attempts, last_error)
                VALUES (?, ?, ?::jsonb, ?, ?)
                """, id, idempotencyKey, writeJson(payload), attempts, error);
        jdbc.update("DELETE FROM saga_event_outbox WHERE id = ?", id);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
