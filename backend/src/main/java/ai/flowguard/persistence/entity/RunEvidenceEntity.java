package ai.flowguard.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ai.flowguard.persistence.MetadataSchema;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "run_evidence", schema = MetadataSchema.NAME)
public class RunEvidenceEntity {

    @Id
    private UUID id;

    @Column(name = "experiment_id", nullable = false)
    private UUID experimentId;

    @Column(name = "oracle_version", nullable = false, length = 20)
    private String oracleVersion;

    @Column(nullable = false, length = 60)
    private String verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private Map<String, Object> beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private Map<String, Object> afterJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> traceJson;

    @Column(name = "record_hash", length = 128)
    private String recordHash;

    @Column(name = "prev_hash", length = 128)
    private String prevHash;

    @Column(name = "manifest_hash", length = 128)
    private String manifestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RunEvidenceEntity() {
    }

    public static RunEvidenceEntity newRecord(
            UUID id,
            UUID experimentId,
            String oracleVersion,
            String verdict,
            Map<String, Object> beforeJson,
            Map<String, Object> afterJson,
            List<Map<String, Object>> traceJson,
            String prevHash,
            String recordHash,
            String manifestHash) {
        RunEvidenceEntity entity = new RunEvidenceEntity();
        entity.id = id;
        entity.experimentId = experimentId;
        entity.oracleVersion = oracleVersion;
        entity.verdict = verdict;
        entity.beforeJson = beforeJson;
        entity.afterJson = afterJson;
        entity.traceJson = traceJson;
        entity.prevHash = prevHash;
        entity.recordHash = recordHash;
        entity.manifestHash = manifestHash;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExperimentId() {
        return experimentId;
    }

    public String getOracleVersion() {
        return oracleVersion;
    }

    public String getVerdict() {
        return verdict;
    }

    public Map<String, Object> getBeforeJson() {
        return beforeJson;
    }

    public Map<String, Object> getAfterJson() {
        return afterJson;
    }

    public List<Map<String, Object>> getTraceJson() {
        return traceJson;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public String getPrevHash() {
        return prevHash;
    }
}
