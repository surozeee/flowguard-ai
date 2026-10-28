package ai.flowguard.evaluation;

import ai.flowguard.experiment.ExperimentExecutor;
import ai.flowguard.experiment.ExperimentResult;
import ai.flowguard.experiment.ExperimentValidator;
import ai.flowguard.experiment.ValidatedExperiment;
import ai.flowguard.meta.AuditingMetaInvestigator;
import ai.flowguard.meta.InvestigationPlan;
import ai.flowguard.meta.MetaProposal;
import ai.flowguard.meta.ProbeEvidence;
import ai.flowguard.persistence.entity.EvaluationEntity;
import ai.flowguard.persistence.jpa.EvaluationJpaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class EvaluationCommandService {

    private final EvaluationJpaRepository evaluations;
    private final AuditingMetaInvestigator auditingMetaInvestigator;
    private final ExperimentValidator experimentValidator;
    private final ExperimentExecutor experimentExecutor;

    public EvaluationCommandService(
            EvaluationJpaRepository evaluations,
            AuditingMetaInvestigator auditingMetaInvestigator,
            ExperimentValidator experimentValidator,
            ExperimentExecutor experimentExecutor) {
        this.evaluations = evaluations;
        this.auditingMetaInvestigator = auditingMetaInvestigator;
        this.experimentValidator = experimentValidator;
        this.experimentExecutor = experimentExecutor;
    }

    public UUID startEvaluation(String projectSlug, String arm, int probeBudget, String objective) {
        UUID evaluationId = UUID.randomUUID();
        evaluations.save(EvaluationEntity.running(evaluationId, projectSlug, arm, probeBudget));
        CompletableFuture.runAsync(() -> runEvaluation(evaluationId, arm, probeBudget, objective));
        return evaluationId;
    }

    void runEvaluation(UUID evaluationId, String arm, int probeBudget, String objective) {
        int probesUsed = 0;
        Integer firstViolationProbe = null;
        List<ProbeEvidence> priorProbes = new ArrayList<>();
        List<Map<String, Object>> plans = new ArrayList<>();
        String preferredExperimentType = null;
        try {
            while (probesUsed < probeBudget) {
                InvestigationPlan plan;
                String source = "RANDOM";
                if ("RANDOM".equalsIgnoreCase(arm)) {
                    plan = RandomPlanGenerator.next(objective);
                } else {
                    MetaProposal proposal = auditingMetaInvestigator.proposeForEvaluation(
                            evaluationId,
                            objective,
                            probeBudget - probesUsed,
                            priorProbes,
                            preferredExperimentType);
                    plan = proposal.plan();
                    source = proposal.source();
                }
                ValidatedExperiment validated = experimentValidator.validate(plan);
                ExperimentResult result = experimentExecutor.execute(validated);
                probesUsed++;
                Map<String, Object> planRow = new LinkedHashMap<>();
                planRow.put("probe", probesUsed);
                planRow.put("source", source);
                planRow.put("experimentType", validated.experimentType());
                planRow.put("oracleVerdict", result.oracleVerdict());
                plans.add(planRow);
                priorProbes.add(new ProbeEvidence(
                        validated.experimentType(), result.oracleVerdict(), result.measurement()));
                if ("NO_VIOLATION".equals(result.oracleVerdict()) || "INCONCLUSIVE".equals(result.oracleVerdict())) {
                    preferredExperimentType = switch (validated.experimentType()) {
                        case "CONCURRENT_DUPLICATE" -> "CONCURRENT_DEBIT";
                        case "CONCURRENT_DEBIT" -> "SAGA_CHOREOGRAPHY_VERIFY";
                        default -> "CONCURRENT_DUPLICATE";
                    };
                }
                if (firstViolationProbe == null && "VIOLATION_REPRODUCED".equals(result.oracleVerdict())) {
                    firstViolationProbe = probesUsed;
                    break;
                }
            }
            Map<String, Object> resultJson = new HashMap<>();
            resultJson.put("probesUsed", probesUsed);
            resultJson.put("firstViolationProbe", firstViolationProbe);
            resultJson.put("arm", arm);
            resultJson.put("plans", plans);
            EvaluationEntity entity = evaluations.findById(evaluationId).orElseThrow();
            entity.complete(probesUsed, firstViolationProbe, resultJson, Instant.now());
            evaluations.save(entity);
        } catch (Exception e) {
            EvaluationEntity entity = evaluations.findById(evaluationId).orElseThrow();
            entity.fail(Map.of("error", e.getMessage()), Instant.now());
            evaluations.save(entity);
        }
    }

    public Map<String, Object> getEvaluation(UUID id) {
        EvaluationEntity entity = evaluations.findById(id).orElseThrow();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("projectSlug", entity.getProjectSlug());
        row.put("arm", entity.getArm());
        row.put("probeBudget", entity.getProbeBudget());
        row.put("probesUsed", entity.getProbesUsed());
        row.put("firstViolationProbe", entity.getFirstViolationProbe());
        row.put("status", entity.getStatus());
        row.put("result", entity.getResultJson());
        row.put("startedAt", entity.getStartedAt());
        row.put("completedAt", entity.getCompletedAt());
        return row;
    }
}
