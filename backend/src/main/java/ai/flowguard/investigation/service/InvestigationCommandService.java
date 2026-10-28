package ai.flowguard.investigation.service;

import java.util.UUID;

public interface InvestigationCommandService {

    UUID startInvestigation(UUID projectId, String objective, String modelId, int maxProbes);
}
