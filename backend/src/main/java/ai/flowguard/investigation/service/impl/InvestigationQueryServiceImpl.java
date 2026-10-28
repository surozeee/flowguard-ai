package ai.flowguard.investigation.service.impl;

import ai.flowguard.investigation.repository.InvestigationRepository;
import ai.flowguard.investigation.service.EvidenceCacheService;
import ai.flowguard.investigation.service.InvestigationQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvestigationQueryServiceImpl implements InvestigationQueryService {

    private final InvestigationRepository investigationRepository;
    private final EvidenceCacheService evidenceCacheService;

    public InvestigationQueryServiceImpl(
            InvestigationRepository investigationRepository,
            EvidenceCacheService evidenceCacheService) {
        this.investigationRepository = investigationRepository;
        this.evidenceCacheService = evidenceCacheService;
    }

    @Override
    public Map<String, Object> getInvestigation(UUID id) {
        Map<String, Object> row = investigationRepository.findById(id);
        evidenceCacheService.getInvestigationState(id.toString())
                .ifPresent(cache -> row.put("cache", cache));
        return row;
    }

    @Override
    public List<Map<String, Object>> listExperiments(UUID investigationId) {
        return investigationRepository.findExperimentsByInvestigationId(investigationId);
    }
}
