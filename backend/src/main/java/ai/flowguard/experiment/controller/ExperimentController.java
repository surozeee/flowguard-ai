package ai.flowguard.experiment.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.experiment.repository.ExperimentRepository;
import ai.flowguard.investigation.service.EvidenceCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@CustomLedgerRestController("/api/v1/experiments")
public class ExperimentController {

    private final ExperimentRepository experimentRepository;
    private final EvidenceCacheService evidenceCacheService;

    public ExperimentController(
            ExperimentRepository experimentRepository,
            EvidenceCacheService evidenceCacheService) {
        this.experimentRepository = experimentRepository;
        this.evidenceCacheService = evidenceCacheService;
    }

    @GetMapping("/{id}/evidence")
    public GlobalResponse<?> evidence(@PathVariable UUID id) {
        Map<String, Object> row = experimentRepository.findLatestEvidence(id);
        evidenceCacheService.getProcessedEvidence(id.toString()).ifPresent(processed -> row.put("processed", processed));
        return GlobalResponse.success(row);
    }
}
