package ai.flowguard.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigations", schema = MetadataSchema.NAME)
public class InvestigationEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 40)
    private String state;

    @Column(name = "model_id", nullable = false, length = 80)
    private String modelId;

    @Column(name = "budget_limit", nullable = false)
    private int budgetLimit;

    @Column(name = "budget_consumed", nullable = false)
    private int budgetConsumed;

    @Column(columnDefinition = "text")
    private String objective;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    protected InvestigationEntity() {
    }

    public static InvestigationEntity created(UUID id, UUID projectId, String modelId, int budgetLimit, String objective) {
        InvestigationEntity entity = new InvestigationEntity();
        entity.id = id;
        entity.projectId = projectId;
        entity.state = "CREATED";
        entity.modelId = modelId;
        entity.budgetLimit = budgetLimit;
        entity.budgetConsumed = 0;
        entity.objective = objective;
        entity.startedAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getModelId() {
        return modelId;
    }

    public int getBudgetLimit() {
        return budgetLimit;
    }

    public int getBudgetConsumed() {
        return budgetConsumed;
    }

    public void setBudgetConsumed(int budgetConsumed) {
        this.budgetConsumed = budgetConsumed;
    }

    public String getObjective() {
        return objective;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
