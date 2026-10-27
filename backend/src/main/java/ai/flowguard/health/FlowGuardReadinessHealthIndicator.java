package ai.flowguard.health;

import ai.flowguard.health.service.HealthService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("flowguardReadiness")
public class FlowGuardReadinessHealthIndicator implements HealthIndicator {

    private final HealthService healthService;

    public FlowGuardReadinessHealthIndicator(HealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public Health health() {
        Map<String, Object> report = healthService.checkDependencies();
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) report.get("dependencies");
        boolean postgresUp = "UP".equals(deps.get("postgres"));
        boolean redisUp = "UP".equals(deps.get("redis"));
        boolean kafkaUp = "UP".equals(deps.get("kafka"));
        if (postgresUp && redisUp && kafkaUp) {
            return Health.up().withDetails(deps).build();
        }
        return Health.outOfService().withDetails(deps).build();
    }
}
