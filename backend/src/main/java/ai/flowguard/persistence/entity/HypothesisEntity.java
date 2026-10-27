package ai.flowguard.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hypotheses", schema = MetadataSchema.NAME)
public class HypothesisEntity {

    @Id
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "invariant_id", nullable = false, length = 120)
    private String invariantId;

    @Column(name = "suspected_mechanism", columnDefinition = "text")
    private String suspectedMechanism;

    @Column(name = "predicted_observation", columnDefinition = "text")
    private String predictedObservation;

    @Column(name = "hypothesis_text", columnDefinition = "text")
    private String hypothesisText;

    @Column(name = "raw_model_response_hash", length = 128)
    private String rawModelResponseHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HypothesisEntity() {
    }

    public static HypothesisEntity newRecord(
            UUID id,
            UUID investigationId,
            String invariantId,
            String suspectedMechanism,
            String predictedObservation,
            String hypothesisText,
            String rawModelResponseHash) {
        HypothesisEntity entity = new HypothesisEntity();
        entity.id = id;
        entity.investigationId = investigationId;
        entity.invariantId = invariantId;
        entity.suspectedMechanism = suspectedMechanism;
        entity.predictedObservation = predictedObservation;
        entity.hypothesisText = hypothesisText;
        entity.rawModelResponseHash = rawModelResponseHash;
        entity.createdAt = Instant.now();
        return entity;
    }

    public String getInvariantId() {
        return invariantId;
    }

    public String getHypothesisText() {
        return hypothesisText;
    }
}
