package ai.flowguard.investigation.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.investigation.payload.request.StartInvestigationRequest;
import ai.flowguard.investigation.payload.response.StartInvestigationResponse;
import ai.flowguard.investigation.service.InvestigationCommandService;
import ai.flowguard.investigation.service.InvestigationEventService;
import ai.flowguard.investigation.service.InvestigationIdempotencyService;
import ai.flowguard.investigation.service.InvestigationQueryService;
import ai.flowguard.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.UUID;

@CustomLedgerRestController("/api/v1/investigations")
public class InvestigationController {

    private final InvestigationCommandService investigationCommandService;
    private final InvestigationQueryService investigationQueryService;
    private final ProjectService projectService;
    private final InvestigationEventService investigationEventService;
    private final InvestigationIdempotencyService investigationIdempotencyService;

    public InvestigationController(
            InvestigationCommandService investigationCommandService,
            InvestigationQueryService investigationQueryService,
            ProjectService projectService,
            InvestigationEventService investigationEventService,
            InvestigationIdempotencyService investigationIdempotencyService) {
        this.investigationCommandService = investigationCommandService;
        this.investigationQueryService = investigationQueryService;
        this.projectService = projectService;
        this.investigationEventService = investigationEventService;
        this.investigationIdempotencyService = investigationIdempotencyService;
    }

    @PostMapping
    public ResponseEntity<GlobalResponse<StartInvestigationResponse>> start(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StartInvestigationRequest request) {
        var replay = investigationIdempotencyService.findExisting(idempotencyKey);
        if (replay.isPresent()) {
            StartInvestigationResponse cached = buildStartResponse(replay.get());
            return ResponseEntity.ok(GlobalResponse.success("Investigation idempotent replay", cached));
        }

        UUID projectId = projectService.resolveProjectId(request.projectId());
        String modelId = request.modelId() == null ? "muse-spark-1.3" : request.modelId();
        int maxProbes = request.maxProbes() == null ? 10 : request.maxProbes();
        UUID id = investigationCommandService.startInvestigation(
                projectId, request.objective(), modelId, maxProbes);

        StartInvestigationResponse body = buildStartResponse(id, modelId, maxProbes);
        investigationIdempotencyService.record(idempotencyKey, id, Map.of(
                "id", id.toString(),
                "modelId", modelId,
                "maxProbes", maxProbes));

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GlobalResponse.success("Investigation accepted", body));
    }

    private StartInvestigationResponse buildStartResponse(UUID id) {
        Map<String, Object> inv = investigationQueryService.getInvestigation(id);
        String modelId = String.valueOf(inv.getOrDefault("modelId", "muse-spark-1.3"));
        int maxProbes = ((Number) inv.getOrDefault("maxProbes", 10)).intValue();
        return buildStartResponse(id, modelId, maxProbes);
    }

    private static StartInvestigationResponse buildStartResponse(UUID id, String modelId, int maxProbes) {
        return new StartInvestigationResponse(
                id, "CREATED", modelId, maxProbes, 0,
                "/api/v1/investigations/" + id + "/events");
    }

    @GetMapping("/{id}")
    public GlobalResponse<?> get(@PathVariable UUID id) {
        return GlobalResponse.success(investigationQueryService.getInvestigation(id));
    }

    @GetMapping("/{id}/experiments")
    public GlobalResponse<?> experiments(@PathVariable UUID id) {
        return GlobalResponse.success(investigationQueryService.listExperiments(id));
    }

    @GetMapping("/{id}/event-log")
    public GlobalResponse<?> eventLog(@PathVariable UUID id) {
        return GlobalResponse.success(investigationEventService.listEvents(id));
    }
}
