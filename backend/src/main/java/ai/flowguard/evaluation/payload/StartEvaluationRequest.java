package ai.flowguard.evaluation.payload;

public record StartEvaluationRequest(
        String projectSlug,
        String arm,
        Integer probeBudget,
        String objective
) {
}
