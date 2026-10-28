package ai.flowguard.investigation.service.impl;

import ai.flowguard.config.FlowGuardProperties;
import ai.flowguard.evidence.EvidenceHashService;
import ai.flowguard.experiment.ExperimentExecutor;
import ai.flowguard.experiment.ExperimentResult;
import ai.flowguard.experiment.ExperimentValidator;
import ai.flowguard.experiment.InvalidExperimentException;
import ai.flowguard.experiment.ValidatedExperiment;
import ai.flowguard.investigation.service.EvidenceCacheService;
import ai.flowguard.investigation.service.InvestigationCommandService;
import ai.flowguard.investigation.service.InvestigationEventService;
import ai.flowguard.messaging.EventPublisher;
import ai.flowguard.meta.AuditingMetaInvestigator;
import ai.flowguard.meta.InvestigationPlan;
import ai.flowguard.meta.MetaJsonResult;
import ai.flowguard.meta.MetaProposal;
import ai.flowguard.meta.ModelUnavailableException;
import ai.flowguard.meta.ProposeRequest;
import ai.flowguard.meta.ProbeEvidence;
import ai.flowguard.persistence.entity.ExperimentEntity;
import ai.flowguard.persistence.entity.HypothesisEntity;
import ai.flowguard.persistence.entity.InvestigationEntity;
import ai.flowguard.persistence.entity.RunEvidenceEntity;
import ai.flowguard.persistence.jpa.ExperimentJpaRepository;
import ai.flowguard.persistence.jpa.HypothesisJpaRepository;
import ai.flowguard.persistence.jpa.InvestigationJpaRepository;
import ai.flowguard.persistence.jpa.RunEvidenceJpaRepository;
import ai.flowguard.saga.SagaTraceVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class InvestigationCommandServiceImpl implements InvestigationCommandService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationCommandServiceImpl.class);

    private final InvestigationJpaRepository investigations;
    private final HypothesisJpaRepository hypotheses;
    private final ExperimentJpaRepository experiments;
    private final RunEvidenceJpaRepository runEvidence;
    private final AuditingMetaInvestigator auditingMetaInvestigator;
    private final ExperimentValidator experimentValidator;
    private final ExperimentExecutor experimentExecutor;
    private final EventPublisher eventPublisher;
    private final EvidenceCacheService evidenceCacheService;
    private final InvestigationEventService investigationEventService;
    private final EvidenceHashService evidenceHashService;
    private final SagaTraceVerifier sagaTraceVerifier;
    private final FlowGuardProperties flowGuardProperties;
    private final TransactionTemplate transactionTemplate;
    private final boolean syncPipeline;

    public InvestigationCommandServiceImpl(
            InvestigationJpaRepository investigations,
            HypothesisJpaRepository hypotheses,
            ExperimentJpaRepository experiments,
            RunEvidenceJpaRepository runEvidence,
            AuditingMetaInvestigator auditingMetaInvestigator,
            ExperimentValidator experimentValidator,
            ExperimentExecutor experimentExecutor,
            EventPublisher eventPublisher,
            EvidenceCacheService evidenceCacheService,
            InvestigationEventService investigationEventService,
            EvidenceHashService evidenceHashService,
            SagaTraceVerifier sagaTraceVerifier,
            FlowGuardProperties flowGuardProperties,
            TransactionTemplate transactionTemplate,
            @Value("${flowguard.investigation.sync-pipeline:false}") boolean syncPipeline) {
        this.investigations = investigations;
        this.hypotheses = hypotheses;
        this.experiments = experiments;
        this.runEvidence = runEvidence;
        this.auditingMetaInvestigator = auditingMetaInvestigator;
        this.experimentValidator = experimentValidator;
        this.experimentExecutor = experimentExecutor;
        this.eventPublisher = eventPublisher;
        this.evidenceCacheService = evidenceCacheService;
        this.investigationEventService = investigationEventService;
        this.evidenceHashService = evidenceHashService;
        this.sagaTraceVerifier = sagaTraceVerifier;
        this.flowGuardProperties = flowGuardProperties;
        this.transactionTemplate = transactionTemplate;
        this.syncPipeline = syncPipeline;
    }

    @Override
    public UUID startInvestigation(UUID projectId, String objective, String modelId, int maxProbes) {
        UUID investigationId = UUID.randomUUID();
        investigations.save(InvestigationEntity.created(investigationId, projectId, modelId, maxProbes, objective));

        eventPublisher.publishInvestigation(investigationId.toString(), "investigation.created", Map.of(
                "objective", objective, "maxProbes", maxProbes));
        investigationEventService.publish(investigationId, "investigation.created", Map.of(
                "objective", objective, "maxProbes", maxProbes));
        Runnable pipeline = () -> {
            try {
                runPipeline(investigationId, objective, maxProbes);
            } catch (Exception ex) {
                log.error("Investigation pipeline failed for {}", investigationId, ex);
                fail(investigationId, "EXECUTION_FAILED", ex.getMessage());
            }
        };
        if (syncPipeline) {
            pipeline.run();
        } else {
            CompletableFuture.runAsync(pipeline);
        }
        return investigationId;
    }

    void runPipeline(UUID investigationId, String objective, int maxProbes) {
        String prevEvidenceHash = "genesis";
        try {
            updateState(investigationId, "RUNNING");
            int probesUsed = 0;
            boolean violationFound = false;
            List<ProbeEvidence> priorProbes = new ArrayList<>();
            RefinedObjective refined = refineObjective(investigationId, objective);
            String workingObjective = refined.objective();
            String preferredExperimentType = refined.preferredFirstExperimentType();

            while (probesUsed < maxProbes && !violationFound) {
                int remaining = maxProbes - probesUsed;
                updateState(investigationId, "MODEL_PLANNING");
                investigationEventService.publish(investigationId, "model.plan.started", Map.of("remainingProbes", remaining));
                eventPublisher.publishInvestigation(investigationId.toString(), "model.plan.started", Map.of());

                MetaProposal proposal = auditingMetaInvestigator.proposeDetailed(
                        investigationId,
                        new ProposeRequest(workingObjective, remaining, priorProbes, preferredExperimentType));
                InvestigationPlan plan = proposal.plan();
                if (proposal.fallback()) {
                    investigationEventService.publish(investigationId, "model.plan.fallback", Map.of(
                            "source", proposal.source(),
                            "httpStatus", proposal.httpStatus() == null ? 0 : proposal.httpStatus(),
                            "errorClass", proposal.errorClass() == null ? "" : proposal.errorClass()));
                } else {
                    investigationEventService.publish(investigationId, "model.plan.received", Map.of(
                            "source", proposal.source(),
                            "httpStatus", proposal.httpStatus() == null ? 0 : proposal.httpStatus(),
                            "experimentType", plan.experimentType(),
                            "inputTokens", proposal.inputTokens() == null ? 0 : proposal.inputTokens(),
                            "outputTokens", proposal.outputTokens() == null ? 0 : proposal.outputTokens()));
                }

                UUID hypothesisId = UUID.randomUUID();
                hypotheses.save(HypothesisEntity.newRecord(
                        hypothesisId,
                        investigationId,
                        plan.invariantId(),
                        plan.suspectedMechanism(),
                        plan.predictedViolation(),
                        plan.hypothesis(),
                        sha256(plan.toString())));

                investigationEventService.publish(investigationId, "hypothesis.recorded", Map.of(
                        "hypothesisId", hypothesisId.toString(),
                        "invariantId", plan.invariantId(),
                        "experimentType", plan.experimentType(),
                        "source", proposal.source()));

                ValidatedExperiment validated;
                try {
                    validated = experimentValidator.validate(plan);
                } catch (InvalidExperimentException ex) {
                    investigationEventService.publish(investigationId, "model.plan.fallback", Map.of(
                            "source", MetaProposal.FALLBACK,
                            "reason", ex.getMessage() == null ? "INVALID_PLAN" : ex.getMessage()));
                    plan = ai.flowguard.meta.MetaModelClient.defaultPlan(
                            workingObjective, remaining, preferredExperimentType);
                    validated = experimentValidator.validate(plan);
                }
                eventPublisher.publishInvestigation(investigationId.toString(), "model.plan.received", Map.of(
                        "hypothesisId", hypothesisId.toString(), "plan", plan, "source", proposal.source()));
                eventPublisher.publishInvestigation(investigationId.toString(), "plan.accepted", Map.of());

                UUID experimentId = UUID.randomUUID();
                experiments.save(ExperimentEntity.running(
                        experimentId, investigationId, hypothesisId, validated.experimentType(), validated.parameters()));

                investigationEventService.publish(investigationId, "experiment.started", Map.of(
                        "experimentId", experimentId.toString(),
                        "hypothesisId", hypothesisId.toString()));

                eventPublisher.publishExperiment(experimentId.toString(), "experiment.started", Map.of(
                        "investigationId", investigationId.toString()));

                ExperimentResult result = experimentExecutor.execute(validated);
                probesUsed++;

                Map<String, Object> recordBody = new HashMap<>();
                recordBody.put("experimentId", experimentId.toString());
                recordBody.put("verdict", result.oracleVerdict());
                recordBody.put("measurement", result.measurement());
                Map<String, String> chain = evidenceHashService.chainForExperiment(prevEvidenceHash, recordBody);
                prevEvidenceHash = chain.get("recordHash");

                UUID evidenceId = UUID.randomUUID();
                runEvidence.save(RunEvidenceEntity.newRecord(
                        evidenceId,
                        experimentId,
                        ai.flowguard.oracles.FinancialOracle.VERSION,
                        result.oracleVerdict(),
                        result.before(),
                        result.after(),
                        result.traces(),
                        chain.get("prevHash"),
                        chain.get("recordHash"),
                        chain.get("manifestHash")));

                ExperimentEntity experiment = experiments.findById(experimentId).orElseThrow();
                experiment.complete(result.oracleVerdict(), Instant.now());
                experiments.save(experiment);

                InvestigationEntity investigation = investigations.findById(investigationId).orElseThrow();
                investigation.setBudgetConsumed(probesUsed);
                investigations.save(investigation);

                Map<String, Object> evidenceEvent = Map.of(
                        "evidenceId", evidenceId.toString(),
                        "experimentId", experimentId.toString(),
                        "investigationId", investigationId.toString(),
                        "verdict", result.oracleVerdict(),
                        "measurement", result.measurement(),
                        "oracleVersion", ai.flowguard.oracles.FinancialOracle.VERSION,
                        "violations", result.oracleDetail().violations(),
                        "recordHash", chain.get("recordHash"),
                        "manifestHash", chain.get("manifestHash"));

                investigationEventService.publish(investigationId, "experiment.finished", evidenceEvent);
                eventPublisher.publishEvidence(experimentId.toString(), evidenceEvent);
                eventPublisher.publishExperiment(experimentId.toString(), "experiment.finished", evidenceEvent);
                preferredExperimentType = publishOracleExplain(investigationId, workingObjective, validated, result);

                Object idempotencyKey = result.measurement().get("idempotencyKey");
                if (flowGuardProperties.kafka().messagingEnabled()
                        && idempotencyKey != null
                        && !idempotencyKey.toString().isBlank()) {
                    var sagaVerdict = sagaTraceVerifier.verifyWithRetry(idempotencyKey.toString(), 20_000);
                    investigationEventService.publish(investigationId, "saga.trace.verified", sagaVerdict.asMap());
                    if (!sagaVerdict.passed()) {
                        violationFound = true;
                        investigationEventService.publish(investigationId, "saga.trace.failed", Map.of(
                                "idempotencyKey", idempotencyKey.toString(),
                                "reason", sagaVerdict.reason() == null ? "UNKNOWN" : sagaVerdict.reason()));
                    }
                }

                evidenceCacheService.cacheInvestigationState(investigationId.toString(), Map.of(
                        "state", result.oracleVerdict(),
                        "lastExperimentId", experimentId.toString(),
                        "measurement", result.measurement(),
                        "probesUsed", probesUsed));

                priorProbes.add(new ProbeEvidence(
                        validated.experimentType(),
                        result.oracleVerdict(),
                        result.measurement()));

                violationFound = "VIOLATION_REPRODUCED".equals(result.oracleVerdict());
            }

            String finalState = violationFound ? "VIOLATION_FOUND" : "COMPLETED";
            InvestigationEntity investigation = investigations.findById(investigationId).orElseThrow();
            investigation.setState(finalState);
            investigation.setCompletedAt(Instant.now());
            investigations.save(investigation);
            investigationEventService.publish(investigationId, "investigation.completed", Map.of(
                    "state", finalState, "probesUsed", probesUsed));
            publishInvestigationSummary(investigationId, workingObjective, finalState, probesUsed, priorProbes);
        } catch (InvalidExperimentException e) {
            investigationEventService.publish(investigationId, "model.plan.failed", Map.of(
                    "code", "PLAN_REJECTED", "message", e.getMessage() == null ? "" : e.getMessage()));
            fail(investigationId, "PLAN_REJECTED", e.getMessage());
        } catch (ModelUnavailableException e) {
            investigationEventService.publish(investigationId, "model.plan.failed", Map.of(
                    "code", "MODEL_UNAVAILABLE", "message", e.getMessage() == null ? "" : e.getMessage()));
            fail(investigationId, "MODEL_UNAVAILABLE", e.getMessage());
        } catch (Exception e) {
            fail(investigationId, "EXECUTION_FAILED", e.getMessage());
        }
    }

    private RefinedObjective refineObjective(UUID investigationId, String objective) {
        try {
            Map<String, Object> stub = Map.of(
                    "refinedObjective", objective,
                    "focusInvariant", "PAYMENT_EFFECT_ONCE",
                    "preferredFirstExperimentType", "CONCURRENT_DUPLICATE");
            MetaJsonResult refined = auditingMetaInvestigator.completeJson(
                    investigationId,
                    AuditingMetaInvestigator.PURPOSE_REFINE,
                    "Return JSON only. Rewrite the objective in plain English. Do not invent oracle results.",
                    """
                            Raw objective: %s
                            Return keys: refinedObjective, focusInvariant, preferredFirstExperimentType
                            refinedObjective: one sentence a reviewer would understand.
                            focusInvariant must be PAYMENT_EFFECT_ONCE or ACCOUNT_RECONCILES.
                            preferredFirstExperimentType must be CONCURRENT_DUPLICATE, CONCURRENT_DEBIT, or SAGA_CHOREOGRAPHY_VERIFY.
                            """.formatted(objective),
                    stub);
            String nextObjective = text(refined, "refinedObjective", objective);
            String preferred = text(refined, "preferredFirstExperimentType", "CONCURRENT_DUPLICATE");
            if (!ai.flowguard.meta.MetaModelClient.ALLOWED_TYPES.contains(preferred)) {
                preferred = "CONCURRENT_DUPLICATE";
            }
            investigationEventService.publish(investigationId, "model.objective.refined", Map.of(
                    "source", refined.source(),
                    "refinedObjective", nextObjective,
                    "focusInvariant", text(refined, "focusInvariant", "PAYMENT_EFFECT_ONCE"),
                    "preferredFirstExperimentType", preferred));
            return new RefinedObjective(nextObjective, preferred);
        } catch (Exception e) {
            log.warn("Meta objective refine skipped for {}", investigationId, e);
            return new RefinedObjective(objective, null);
        }
    }

    private record RefinedObjective(String objective, String preferredFirstExperimentType) {}

    private String publishOracleExplain(
            UUID investigationId, String objective, ValidatedExperiment validated, ExperimentResult result) {
        try {
            Map<String, Object> stub = Map.of(
                    "headline", humanOracleHeadline(result.oracleVerdict()),
                    "explanation",
                    "We ran this probe against the demo wallet. Meta did not grade the result — the ledger checks did. "
                            + humanOracleHeadline(result.oracleVerdict())
                            + " Experiment: "
                            + humanExperimentType(validated.experimentType())
                            + ".",
                    "nextSuggestedExperimentType",
                    "VIOLATION_REPRODUCED".equals(result.oracleVerdict())
                            ? "SAGA_CHOREOGRAPHY_VERIFY"
                            : "CONCURRENT_DEBIT");
            MetaJsonResult explained = auditingMetaInvestigator.completeJson(
                    investigationId,
                    AuditingMetaInvestigator.PURPOSE_EXPLAIN,
                    "Return JSON only. Write like a teammate briefing a reviewer: two short sentences, no buzzwords. Never change or contradict oracleVerdict.",
                    """
                            Objective: %s
                            Experiment: %s
                            Oracle verdict (authoritative): %s
                            Measurement: %s
                            Return keys: headline, explanation, nextSuggestedExperimentType
                            headline: one plain sentence.
                            explanation: two short sentences a human can read aloud.
                            nextSuggestedExperimentType must be one of CONCURRENT_DUPLICATE, CONCURRENT_DEBIT, SAGA_CHOREOGRAPHY_VERIFY.
                            """.formatted(
                            objective,
                            validated.experimentType(),
                            result.oracleVerdict(),
                            result.measurement()),
                    stub);
            String suggested = text(explained, "nextSuggestedExperimentType", stub.get("nextSuggestedExperimentType").toString());
            if (!ai.flowguard.meta.MetaModelClient.ALLOWED_TYPES.contains(suggested)) {
                suggested = stub.get("nextSuggestedExperimentType").toString();
            }
            investigationEventService.publish(investigationId, "model.explain.received", Map.of(
                    "source", explained.source(),
                    "headline", text(explained, "headline", stub.get("headline").toString()),
                    "explanation", text(explained, "explanation", stub.get("explanation").toString()),
                    "nextSuggestedExperimentType", suggested,
                    "oracleVerdict", result.oracleVerdict()));
            return suggested;
        } catch (Exception e) {
            log.warn("Meta explain skipped for {}", investigationId, e);
            return null;
        }
    }

    private void publishInvestigationSummary(
            UUID investigationId, String objective, String finalState, int probesUsed, List<ProbeEvidence> priorProbes) {
        try {
            Map<String, Object> stub = Map.of(
                    "summary",
                    "We finished after " + probesUsed + " probe"
                            + (probesUsed == 1 ? "" : "s")
                            + ". "
                            + humanFinalState(finalState),
                    "recommendedFollowUp",
                    "If the duplicate-credit race is still unproven, run another overlapping payment with the same idempotency key.");
            MetaJsonResult summary = auditingMetaInvestigator.completeJson(
                    investigationId,
                    AuditingMetaInvestigator.PURPOSE_SUMMARY,
                    "Return JSON only. Write a short wrap-up a teammate can read. Do not invent oracle outcomes.",
                    """
                            Objective: %s
                            Final state: %s
                            Probes used: %d
                            Probe evidence: %s
                            Return keys: summary, recommendedFollowUp
                            summary: two plain sentences.
                            recommendedFollowUp: one concrete next step.
                            """.formatted(objective, finalState, probesUsed, priorProbes),
                    stub);
            Map<String, Object> payload = Map.of(
                    "source", summary.source(),
                    "summary", text(summary, "summary", stub.get("summary").toString()),
                    "recommendedFollowUp",
                    text(summary, "recommendedFollowUp", stub.get("recommendedFollowUp").toString()),
                    "state", finalState);
            investigationEventService.publish(investigationId, "model.summary.received", payload);
            evidenceCacheService.cacheInvestigationState(investigationId.toString(), payload);
        } catch (Exception e) {
            log.warn("Meta summary skipped for {}", investigationId, e);
        }
    }

    private static String text(MetaJsonResult result, String field, String fallback) {
        if (result.json() == null || result.json().path(field).isMissingNode() || result.json().path(field).isNull()) {
            return fallback;
        }
        String value = result.json().path(field).asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    private void fail(UUID investigationId, String code, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            InvestigationEntity investigation = investigations.findById(investigationId).orElseThrow();
            investigation.setState("FAILED");
            investigation.setErrorCode(code);
            investigation.setCompletedAt(Instant.now());
            investigations.save(investigation);
        });
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("message", message == null ? "" : message);
        eventPublisher.publishInvestigation(investigationId.toString(), "investigation.failed", payload);
        investigationEventService.publish(investigationId, "investigation.failed", payload);
    }

    private static String humanOracleHeadline(String verdict) {
        if ("VIOLATION_REPRODUCED".equals(verdict)) {
            return "The ledger checks reproduced a real invariant break.";
        }
        if ("NO_VIOLATION_OBSERVED".equals(verdict)) {
            return "The ledger checks did not see a break on this probe.";
        }
        return "The ledger checks were inconclusive.";
    }

    private static String humanExperimentType(String type) {
        return switch (type) {
            case "CONCURRENT_DUPLICATE" -> "overlapping duplicate payments";
            case "CONCURRENT_DEBIT" -> "overlapping debit transfers";
            case "SAGA_CHOREOGRAPHY_VERIFY" -> "end-to-end saga choreography";
            default -> type == null ? "unknown probe" : type;
        };
    }

    private static String humanFinalState(String state) {
        return switch (state) {
            case "VIOLATION_FOUND" -> "We reproduced an invariant break.";
            case "COMPLETED" -> "We did not reproduce a break within the probe budget.";
            default -> "Status: " + state + ".";
        };
    }

    private void updateState(UUID investigationId, String state) {
        transactionTemplate.executeWithoutResult(status -> {
            InvestigationEntity investigation = investigations.findById(investigationId).orElseThrow();
            investigation.setState(state);
            investigations.save(investigation);
        });
        investigationEventService.publish(investigationId, "investigation.state", Map.of("state", state));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "sha256:unavailable";
        }
    }
}
