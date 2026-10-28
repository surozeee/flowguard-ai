package ai.flowguard.investigation.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StartInvestigationRequest(
        @NotBlank String projectId,
        @NotBlank String objective,
        String modelId,
        String promptVersion,
        @Min(1) @Max(10) Integer maxProbes,
        Boolean allowPatchProposals,
        Boolean benchmarkMode
) {
}
