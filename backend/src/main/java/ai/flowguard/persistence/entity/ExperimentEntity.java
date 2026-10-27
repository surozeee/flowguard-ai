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
@Table(name = "experiments", schema = MetadataSchema.NAME)
public class ExperimentEntity {

    @Id
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "hypothesis_id")
    private UUID hypothesisId;

    @Column(name = "experiment_type", nullable = false, length = 60)
    private String experimentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> paramsJson;

    @Column(length = 40, nullable = false)
    private String status;

    @Column(name = "oracle_verdict", length = 60)
    private String oracleVerdict;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ExperimentEntity() {
    }

    public static ExperimentEntity running(
            UUID id, UUID investigationId, UUID hypothesisId, String experimentType, Map<String, Object> paramsJson) {
        ExperimentEntity entity = new ExperimentEntity();
        entity.id = id;
        entity.investigationId = investigationId;
        entity.hypothesisId = hypothesisId;
        entity.experimentType = experimentType;
        entity.paramsJson = paramsJson;
        entity.status = "RUNNING";
        entity.startedAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public void complete(String oracleVerdict, Instant completedAt) {
        this.status = "COMPLETED";
        this.oracleVerdict = oracleVerdict;
        this.completedAt = completedAt;
    }

    public UUID getHypothesisId() {
        return hypothesisId;
    }

    public String getExperimentType() {
        return experimentType;
    }

    public String getStatus() {
        return status;
    }

    public String getOracleVerdict() {
        return oracleVerdict;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
