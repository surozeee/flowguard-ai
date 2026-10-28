package ai.flowguard.experiment.repository;

import ai.flowguard.persistence.entity.ExperimentEntity;
import ai.flowguard.persistence.entity.RunEvidenceEntity;
import ai.flowguard.persistence.jpa.ExperimentJpaRepository;
import ai.flowguard.persistence.jpa.RunEvidenceJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Repository
public class ExperimentRepository {

    private final RunEvidenceJpaRepository runEvidence;
    private final ExperimentJpaRepository experiments;

    public ExperimentRepository(RunEvidenceJpaRepository runEvidence, ExperimentJpaRepository experiments) {
        this.runEvidence = runEvidence;
        this.experiments = experiments;
    }

    public Map<String, Object> findLatestEvidence(UUID experimentId) {
        RunEvidenceEntity evidence =
                runEvidence.findFirstByExperimentIdOrderByCreatedAtDesc(experimentId).orElseThrow();
        ExperimentEntity experiment = experiments.findById(experimentId).orElseThrow();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", evidence.getId());
        row.put("experimentId", evidence.getExperimentId());
        row.put("oracleVersion", evidence.getOracleVersion());
        row.put("oracleVerdict", evidence.getVerdict());
        row.put("beforeSnapshot", evidence.getBeforeJson());
        row.put("afterSnapshot", evidence.getAfterJson());
        row.put("trace", evidence.getTraceJson());
        row.put("recordHash", evidence.getRecordHash());
        row.put("prevHash", evidence.getPrevHash());
        row.put("experimentType", experiment.getExperimentType());
        return row;
    }
}
