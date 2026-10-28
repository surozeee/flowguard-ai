package ai.flowguard.meta;

import ai.flowguard.persistence.entity.ModelCallEntity;
import ai.flowguard.persistence.jpa.ModelCallJpaRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ModelCallAuditService {

    private final ModelCallJpaRepository modelCalls;

    public ModelCallAuditService(ModelCallJpaRepository modelCalls) {
        this.modelCalls = modelCalls;
    }

    public void record(
            UUID investigationId,
            String modelId,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass) {
        record(investigationId, modelId, latencyMs, inputTokens, outputTokens, responseHash, errorClass, null, null);
    }

    public void record(
            UUID investigationId,
            String modelId,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass,
            String source,
            Integer httpStatus) {
        record(investigationId, modelId, latencyMs, inputTokens, outputTokens, responseHash, errorClass, source, httpStatus, null);
    }

    public void record(
            UUID investigationId,
            String modelId,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass,
            String source,
            Integer httpStatus,
            String purpose) {
        record(investigationId, null, modelId, latencyMs, inputTokens, outputTokens, responseHash, errorClass, source, httpStatus, purpose);
    }

    public void record(
            UUID investigationId,
            UUID evaluationId,
            String modelId,
            long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass,
            String source,
            Integer httpStatus,
            String purpose) {
        modelCalls.save(ModelCallEntity.record(
                investigationId,
                evaluationId,
                modelId,
                (int) latencyMs,
                inputTokens,
                outputTokens,
                responseHash,
                errorClass,
                source,
                httpStatus,
                purpose));
    }
}
