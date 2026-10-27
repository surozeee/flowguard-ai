package ai.flowguard.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "investigation_events", schema = MetadataSchema.NAME)
public class InvestigationEventEntity {

    @Id
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvestigationEventEntity() {
    }

    public static InvestigationEventEntity newRecord(
            UUID id, UUID investigationId, long sequenceNo, String eventType, Map<String, Object> payloadJson) {
        InvestigationEventEntity entity = new InvestigationEventEntity();
        entity.id = id;
        entity.investigationId = investigationId;
        entity.sequenceNo = sequenceNo;
        entity.eventType = eventType;
        entity.payloadJson = payloadJson;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
