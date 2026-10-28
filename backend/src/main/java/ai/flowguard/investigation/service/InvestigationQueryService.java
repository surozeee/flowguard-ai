package ai.flowguard.investigation.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InvestigationQueryService {

    Map<String, Object> getInvestigation(UUID id);

    List<Map<String, Object>> listExperiments(UUID investigationId);
}
