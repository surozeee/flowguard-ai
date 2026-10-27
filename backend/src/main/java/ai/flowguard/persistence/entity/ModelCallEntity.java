package ai.flowguard.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_calls", schema = MetadataSchema.NAME)
public class ModelCallEntity {

    @Id
    private UUID id;

    @Column(name = "investigation_id")
    private UUID investigationId;

    @Column(name = "evaluation_id")
    private UUID evaluationId;

    @Column(name = "model_id", nullable = false, length = 80)
    private String modelId;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "response_hash", length = 128)
    private String responseHash;

    @Column(name = "error_class", length = 80)
    private String errorClass;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "purpose", length = 40)
    private String purpose;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModelCallEntity() {
    }

    public static ModelCallEntity record(
            UUID investigationId,
            String modelId,
            int latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass) {
        return record(investigationId, modelId, latencyMs, inputTokens, outputTokens, responseHash, errorClass, null, null);
    }

    public static ModelCallEntity record(
            UUID investigationId,
            String modelId,
            int latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass,
            String source,
            Integer httpStatus) {
        return record(
                investigationId,
                modelId,
                latencyMs,
                inputTokens,
                outputTokens,
                responseHash,
                errorClass,
                source,
                httpStatus,
                null);
    }

    public static ModelCallEntity record(
            UUID investigationId,
            String modelId,
            int latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass,
            String source,
            Integer httpStatus,
            String purpose) {
        return record(
                investigationId,
                null,
                modelId,
                latencyMs,
                inputTokens,
                outputTokens,
                responseHash,
                errorClass,
                source,
                httpStatus,
                purpose);
    }

    public static ModelCallEntity record(
            UUID investigationId,
            UUID evaluationId,
            String modelId,
            int latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass,
            String source,
            Integer httpStatus,
            String purpose) {
        ModelCallEntity entity = new ModelCallEntity();
        entity.id = UUID.randomUUID();
        entity.investigationId = investigationId;
        entity.evaluationId = evaluationId;
        entity.modelId = modelId;
        entity.latencyMs = latencyMs;
        entity.inputTokens = inputTokens;
        entity.outputTokens = outputTokens;
        entity.responseHash = responseHash;
        entity.errorClass = errorClass;
        entity.source = source;
        entity.httpStatus = httpStatus;
        entity.purpose = purpose;
        entity.createdAt = Instant.now();
        return entity;
    }
}
