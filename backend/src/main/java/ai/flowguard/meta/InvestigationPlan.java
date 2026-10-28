package ai.flowguard.meta;

import java.util.Map;

public record InvestigationPlan(
        String invariantId,
        String hypothesis,
        String suspectedMechanism,
        String experimentType,
        Map<String, Object> parameters,
        String predictedViolation,
        String reasonForChoice
) {
}
