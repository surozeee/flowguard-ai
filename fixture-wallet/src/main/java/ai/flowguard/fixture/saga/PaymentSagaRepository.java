package ai.flowguard.fixture.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PaymentSagaRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PaymentSagaRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insert(UUID sagaId, String idempotencyKey, String sagaType, UUID accountId, Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO payment_sagas (id, idempotency_key, saga_type, state, current_step, account_id, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, sagaId, idempotencyKey, sagaType, PaymentSagaState.STARTED.name(), "START", accountId, writeJson(payload));
    }

    public void insertCompletedReplay(
            UUID sagaId,
            String idempotencyKey,
            String sagaType,
            UUID accountId,
            UUID paymentId,
            Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO payment_sagas (id, idempotency_key, saga_type, state, current_step, account_id, payment_id, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, sagaId, idempotencyKey, sagaType, PaymentSagaState.COMPLETED.name(), "REPLAY", accountId, paymentId,
                writeJson(payload));
    }

    public List<Map<String, Object>> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.queryForList("""
                SELECT id, idempotency_key, saga_type, state, current_step, account_id, payment_id, failure_reason, created_at, updated_at
                FROM payment_sagas
                WHERE idempotency_key = ?
                ORDER BY created_at ASC
                """, idempotencyKey);
    }

    public List<Map<String, Object>> findStaleActive(java.time.Instant updatedBefore) {
        return jdbc.queryForList("""
                SELECT id, idempotency_key, state, current_step, account_id, updated_at
                FROM payment_sagas
                WHERE state NOT IN ('COMPLETED', 'FAILED', 'COMPENSATED')
                  AND updated_at < ?
                ORDER BY updated_at ASC
                LIMIT 100
                """, java.sql.Timestamp.from(updatedBefore));
    }

    public void advance(UUID sagaId, PaymentSagaState state, String step, UUID paymentId) {
        if (paymentId == null) {
            jdbc.update("""
                    UPDATE payment_sagas SET state = ?, current_step = ?, updated_at = now() WHERE id = ?
                    """, state.name(), step, sagaId);
        } else {
            jdbc.update("""
                    UPDATE payment_sagas SET state = ?, current_step = ?, payment_id = ?, updated_at = now() WHERE id = ?
                    """, state.name(), step, paymentId, sagaId);
        }
    }

    public void fail(UUID sagaId, String reason) {
        jdbc.update("""
                UPDATE payment_sagas SET state = ?, current_step = 'FAILED', failure_reason = ?, updated_at = now()
                WHERE id = ?
                """, PaymentSagaState.FAILED.name(), reason, sagaId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
