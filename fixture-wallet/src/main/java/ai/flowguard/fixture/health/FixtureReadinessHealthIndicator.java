package ai.flowguard.fixture.health;

import ai.flowguard.fixture.health.service.FixtureHealthService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("fixtureReadiness")
public class FixtureReadinessHealthIndicator implements HealthIndicator {

    private final FixtureHealthService fixtureHealthService;

    public FixtureReadinessHealthIndicator(FixtureHealthService fixtureHealthService) {
        this.fixtureHealthService = fixtureHealthService;
    }

    @Override
    public Health health() {
        Map<String, Object> report = fixtureHealthService.check();
        @SuppressWarnings("unchecked")
        Map<String, String> deps = (Map<String, String>) report.get("dependencies");
        boolean postgresOk = "UP".equals(deps.get("postgres"));
        boolean redisOk = "UP".equals(deps.get("redis")) || "DISABLED".equals(deps.get("redis"));
        boolean kafkaOk = "UP".equals(deps.get("kafka")) || "DISABLED".equals(deps.get("kafka"));
        if (postgresOk && redisOk && kafkaOk) {
            return Health.up().withDetails(deps).build();
        }
        return Health.outOfService().withDetails(deps).build();
    }
}
