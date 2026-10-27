package ai.flowguard.health.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.health.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;

@CustomLedgerRestController("/api/v1")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public GlobalResponse<?> health() {
        return GlobalResponse.success(healthService.checkDependencies());
    }
}
