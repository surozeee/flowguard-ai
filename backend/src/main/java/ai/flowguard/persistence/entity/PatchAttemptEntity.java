package ai.flowguard.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patch_attempts", schema = MetadataSchema.NAME)
public class PatchAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "candidate_diff_hash", nullable = false, length = 128)
    private String candidateDiffHash;

    @Column(name = "approval_status", nullable = false, length = 40)
    private String approvalStatus;

    @Column(name = "build_status", length = 40)
    private String buildStatus;

    @Column(name = "immutable_test_hash", length = 128)
    private String immutableTestHash;

    @Column(length = 60)
    private String verdict;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PatchAttemptEntity() {
    }

    public static PatchAttemptEntity queued(
            UUID id,
            UUID investigationId,
            String candidateDiffHash,
            String approvalStatus,
            String immutableTestHash,
            String verdict) {
        PatchAttemptEntity entity = new PatchAttemptEntity();
        entity.id = id;
        entity.investigationId = investigationId;
        entity.candidateDiffHash = candidateDiffHash;
        entity.approvalStatus = approvalStatus;
        entity.buildStatus = "QUEUED";
        entity.immutableTestHash = immutableTestHash;
        entity.verdict = verdict;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public void markBuildPassed() {
        this.buildStatus = "PASSED";
        this.verdict = "IMMUTABLE_TESTS_PASSED";
    }

    public void markBuildFailed(String verdict) {
        this.buildStatus = "FAILED";
        this.verdict = verdict;
    }
}
