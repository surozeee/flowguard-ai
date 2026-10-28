package ai.flowguard.meta;

import ai.flowguard.config.FlowGuardProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditingMetaInvestigator {

    public static final String PURPOSE_PLAN = "PLAN";
    public static final String PURPOSE_EXPLAIN = "EXPLAIN";
    public static final String PURPOSE_SUMMARY = "SUMMARY";
    public static final String PURPOSE_PATCH = "PATCH";
    public static final String PURPOSE_REFINE = "REFINE";

    private final MetaModelClient metaModelClient;
    private final ModelCallAuditService modelCallAuditService;
    private final FlowGuardProperties properties;

    public AuditingMetaInvestigator(
            MetaModelClient metaModelClient,
            ModelCallAuditService modelCallAuditService,
            FlowGuardProperties properties) {
        this.metaModelClient = metaModelClient;
        this.modelCallAuditService = modelCallAuditService;
        this.properties = properties;
    }

    public InvestigationPlan proposeForInvestigation(UUID investigationId, String objective, int remainingProbes) {
        return proposeDetailed(investigationId, ProposeRequest.of(objective, remainingProbes)).plan();
    }

    public MetaProposal proposeDetailed(
            UUID investigationId, String objective, int remainingProbes, List<ProbeEvidence> priorProbes) {
        return proposeDetailed(investigationId, new ProposeRequest(objective, remainingProbes, priorProbes));
    }

    public MetaProposal proposeForEvaluation(
            UUID evaluationId, String objective, int remainingProbes, List<ProbeEvidence> priorProbes) {
        return proposeForEvaluation(evaluationId, objective, remainingProbes, priorProbes, null);
    }

    public MetaProposal proposeForEvaluation(
            UUID evaluationId,
            String objective,
            int remainingProbes,
            List<ProbeEvidence> priorProbes,
            String preferredExperimentType) {
        return propose(null, evaluationId, new ProposeRequest(objective, remainingProbes, priorProbes, preferredExperimentType));
    }

    public MetaProposal proposeDetailed(UUID investigationId, ProposeRequest request) {
        return propose(investigationId, null, request);
    }

    private MetaProposal propose(UUID investigationId, UUID evaluationId, ProposeRequest request) {
        long start = System.currentTimeMillis();
        try {
            MetaProposal proposal = metaModelClient.proposeDetailed(request);
            audit(
                    investigationId,
                    evaluationId,
                    PURPOSE_PLAN,
                    start,
                    proposal.inputTokens(),
                    proposal.outputTokens(),
                    sha256(proposal.plan().toString()),
                    proposal.errorClass(),
                    proposal.source(),
                    proposal.httpStatus());
            return proposal;
        } catch (ModelUnavailableException e) {
            audit(
                    investigationId,
                    evaluationId,
                    PURPOSE_PLAN,
                    start,
                    null,
                    null,
                    null,
                    e.getClass().getSimpleName(),
                    MetaProposal.FALLBACK,
                    null);
            throw e;
        }
    }

    public MetaJsonResult completeJson(
            UUID correlationId,
            String purpose,
            String systemPrompt,
            String userPrompt,
            Map<String, Object> stubPayload) {
        long start = System.currentTimeMillis();
        MetaJsonResult result = metaModelClient.completeJson(systemPrompt, userPrompt, stubPayload);
        audit(
                correlationId,
                null,
                purpose,
                start,
                result.inputTokens(),
                result.outputTokens(),
                sha256(result.json() == null ? "" : result.json().toString()),
                result.errorClass(),
                result.source(),
                result.httpStatus());
        return result;
    }

    private void audit(
            UUID investigationId,
            UUID evaluationId,
            String purpose,
            long start,
            Integer inputTokens,
            Integer outputTokens,
            String responseHash,
            String errorClass,
            String source,
            Integer httpStatus) {
        modelCallAuditService.record(
                investigationId,
                evaluationId,
                properties.meta().modelId(),
                System.currentTimeMillis() - start,
                inputTokens,
                outputTokens,
                responseHash,
                errorClass,
                source,
                httpStatus,
                purpose);
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
