package ai.flowguard.persistence.entity;

import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "saga_choreography_log",
        schema = MetadataSchema.NAME,
        uniqueConstraints = @UniqueConstraint(columnNames = {"saga_id", "event_type", "step"}))
public class SagaChoreographyLogEntity {

    @Id
    private UUID id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "saga_state", length = 40)
    private String sagaState;

    @Column(length = 60)
    private String step;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected SagaChoreographyLogEntity() {
    }

    public static SagaChoreographyLogEntity newEvent(
            UUID sagaId,
            String idempotencyKey,
            String eventType,
            String sagaState,
            String step,
            Map<String, Object> payloadJson) {
        SagaChoreographyLogEntity entity = new SagaChoreographyLogEntity();
        entity.id = UUID.randomUUID();
        entity.sagaId = sagaId;
        entity.idempotencyKey = idempotencyKey;
        entity.eventType = eventType;
        entity.sagaState = sagaState;
        entity.step = step;
        entity.payloadJson = payloadJson;
        entity.recordedAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSagaState() {
        return sagaState;
    }

    public String getStep() {
        return step;
    }

    public Map<String, Object> getPayloadJson() {
        return payloadJson;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
