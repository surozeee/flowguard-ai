package ai.flowguard.evaluation.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.evaluation.EvaluationCommandService;
import ai.flowguard.evaluation.payload.StartEvaluationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@CustomLedgerRestController("/api/v1/evaluations")
public class EvaluationController {

    private final EvaluationCommandService evaluationCommandService;

    public EvaluationController(EvaluationCommandService evaluationCommandService) {
        this.evaluationCommandService = evaluationCommandService;
    }

    @PostMapping
    public ResponseEntity<GlobalResponse<Map<String, Object>>> start(@Valid @RequestBody StartEvaluationRequest request) {
        String arm = request.arm() == null ? "META" : request.arm();
        int budget = request.probeBudget() == null ? 3 : request.probeBudget();
        String slug = request.projectSlug() == null ? "sample-wallet" : request.projectSlug();
        String objective = request.objective() == null ? "Find idempotency race" : request.objective();
        UUID id = evaluationCommandService.startEvaluation(slug, arm, budget, objective);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GlobalResponse.success(
                "Evaluation started",
                Map.of("evaluationId", id, "arm", arm, "probeBudget", budget)));
    }

    @GetMapping("/{id}")
    public GlobalResponse<Map<String, Object>> get(@PathVariable UUID id) {
        return GlobalResponse.success(evaluationCommandService.getEvaluation(id));
    }
}
