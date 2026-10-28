package ai.flowguard.investigation.payload.response;

import java.util.UUID;

public record StartInvestigationResponse(
        UUID id,
        String state,
        String modelId,
        int maxProbes,
        int probesUsed,
        String eventsUrl
) {
}
