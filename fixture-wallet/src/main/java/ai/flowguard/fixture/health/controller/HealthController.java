package ai.flowguard.fixture.health.controller;

import ai.flowguard.fixture.health.service.FixtureHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final FixtureHealthService fixtureHealthService;

    public HealthController(FixtureHealthService fixtureHealthService) {
        this.fixtureHealthService = fixtureHealthService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return fixtureHealthService.check();
    }
}
