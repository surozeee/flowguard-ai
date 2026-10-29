package ai.flowguard.saga;

import ai.flowguard.persistence.entity.SagaChoreographyLogEntity;
import ai.flowguard.persistence.jpa.SagaChoreographyLogJpaRepository;
import ai.flowguard.saga.payload.SagaTraceRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SagaChoreographyRepository {

    private final SagaChoreographyLogJpaRepository jpa;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SagaChoreographyRepository(
            SagaChoreographyLogJpaRepository jpa, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.jdbc = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean appendIfAbsent(
            UUID sagaId,
            String idempotencyKey,
            String eventType,
            String sagaState,
            String step,
            Map<String, Object> payload) {
        int inserted = jdbc.update("""
                INSERT INTO metadata.saga_choreography_log (id, saga_id, idempotency_key, event_type, saga_state, step, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (saga_id, event_type, step) DO NOTHING
                """,
                UUID.randomUUID(),
                sagaId,
                idempotencyKey,
                eventType,
                sagaState,
                step,
                writeJson(payload));
        return inserted > 0;
    }

    public List<Map<String, Object>> findIncompleteChoreography(int limit) {
        return jdbc.queryForList("""
                SELECT s.idempotency_key, COUNT(*) AS event_count, MAX(s.recorded_at) AS last_event_at
                FROM metadata.saga_choreography_log s
                WHERE s.event_type = 'SAGA_STARTED'
                  AND s.idempotency_key IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM metadata.saga_choreography_log t
                    WHERE t.idempotency_key = s.idempotency_key
                      AND t.event_type IN ('SAGA_COMPLETED', 'SAGA_IDEMPOTENT_REPLAY')
                  )
                GROUP BY s.idempotency_key
                ORDER BY last_event_at DESC
                LIMIT ?
                """, limit);
    }

    public List<Map<String, Object>> findRecent(int limit) {
        return jpa.findAllByOrderByRecordedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toRecentRow)
                .toList();
    }

    public int countEvents() {
        return Math.toIntExact(jpa.count());
    }

    public List<SagaTraceRow> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKeyOrderByRecordedAtAsc(idempotencyKey).stream()
                .map(this::toTraceRow)
                .toList();
    }

    private Map<String, Object> toRecentRow(SagaChoreographyLogEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("saga_id", entity.getSagaId());
        row.put("idempotency_key", entity.getIdempotencyKey());
        row.put("event_type", entity.getEventType());
        row.put("saga_state", entity.getSagaState());
        row.put("step", entity.getStep());
        row.put("recorded_at", entity.getRecordedAt());
        return row;
    }

    private SagaTraceRow toTraceRow(SagaChoreographyLogEntity entity) {
        return new SagaTraceRow(
                entity.getId(),
                entity.getSagaId(),
                entity.getIdempotencyKey(),
                entity.getEventType(),
                entity.getSagaState(),
                entity.getStep(),
                entity.getPayloadJson(),
                entity.getRecordedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
