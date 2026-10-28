package ai.flowguard.investigation.repository;

import ai.flowguard.persistence.entity.ExperimentEntity;
import ai.flowguard.persistence.entity.HypothesisEntity;
import ai.flowguard.persistence.entity.InvestigationEntity;
import ai.flowguard.persistence.jpa.ExperimentJpaRepository;
import ai.flowguard.persistence.jpa.HypothesisJpaRepository;
import ai.flowguard.persistence.jpa.InvestigationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class InvestigationRepository {

    private final InvestigationJpaRepository investigations;
    private final ExperimentJpaRepository experiments;
    private final HypothesisJpaRepository hypotheses;

    public InvestigationRepository(
            InvestigationJpaRepository investigations,
            ExperimentJpaRepository experiments,
            HypothesisJpaRepository hypotheses) {
        this.investigations = investigations;
        this.experiments = experiments;
        this.hypotheses = hypotheses;
    }

    public Map<String, Object> findById(UUID id) {
        InvestigationEntity entity = investigations.findById(id).orElseThrow();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("state", entity.getState());
        row.put("modelId", entity.getModelId());
        row.put("maxProbes", entity.getBudgetLimit());
        row.put("probesUsed", entity.getBudgetConsumed());
        row.put("objective", entity.getObjective());
        row.put("errorCode", entity.getErrorCode());
        return row;
    }

    public List<Map<String, Object>> findExperimentsByInvestigationId(UUID investigationId) {
        return experiments.findByInvestigationIdOrderByStartedAtAsc(investigationId).stream()
                .map(this::experimentRow)
                .toList();
    }

    private Map<String, Object> experimentRow(ExperimentEntity experiment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", experiment.getId());
        row.put("experimentType", experiment.getExperimentType());
        row.put("status", experiment.getStatus());
        row.put("oracleVerdict", experiment.getOracleVerdict());
        row.put("startedAt", experiment.getStartedAt());
        row.put("completedAt", experiment.getCompletedAt());
        UUID hypothesisId = experiment.getHypothesisId();
        if (hypothesisId != null) {
            HypothesisEntity hypothesis = hypotheses.findById(hypothesisId).orElse(null);
            if (hypothesis != null) {
                row.put("invariantId", hypothesis.getInvariantId());
                row.put("hypothesis", hypothesis.getHypothesisText());
            }
        }
        return row;
    }
}
