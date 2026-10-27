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
@Table(name = "evaluations", schema = MetadataSchema.NAME)
public class EvaluationEntity {

    @Id
    private UUID id;

    @Column(name = "project_slug", nullable = false, length = 80)
    private String projectSlug;

    @Column(nullable = false, length = 40)
    private String arm;

    @Column(name = "probe_budget", nullable = false)
    private int probeBudget;

    @Column(name = "probes_used", nullable = false)
    private int probesUsed;

    @Column(name = "first_violation_probe")
    private Integer firstViolationProbe;

    @Column(nullable = false, length = 40)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> resultJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected EvaluationEntity() {
    }

    public static EvaluationEntity running(UUID id, String projectSlug, String arm, int probeBudget) {
        EvaluationEntity entity = new EvaluationEntity();
        entity.id = id;
        entity.projectSlug = projectSlug;
        entity.arm = arm;
        entity.probeBudget = probeBudget;
        entity.probesUsed = 0;
        entity.status = "RUNNING";
        entity.startedAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public void complete(int probesUsed, Integer firstViolationProbe, Map<String, Object> resultJson, Instant completedAt) {
        this.probesUsed = probesUsed;
        this.firstViolationProbe = firstViolationProbe;
        this.status = "COMPLETED";
        this.resultJson = resultJson;
        this.completedAt = completedAt;
    }

    public void fail(Map<String, Object> resultJson, Instant completedAt) {
        this.status = "FAILED";
        this.resultJson = resultJson;
        this.completedAt = completedAt;
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public String getArm() {
        return arm;
    }

    public int getProbeBudget() {
        return probeBudget;
    }

    public int getProbesUsed() {
        return probesUsed;
    }

    public Integer getFirstViolationProbe() {
        return firstViolationProbe;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getResultJson() {
        return resultJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
