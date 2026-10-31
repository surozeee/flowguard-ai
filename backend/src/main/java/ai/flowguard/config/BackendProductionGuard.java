package ai.flowguard.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@ConditionalOnProperty(name = "flowguard.production.guards-enabled", havingValue = "true")
public class BackendProductionGuard implements ApplicationRunner {

    private final FlowGuardProperties properties;

    public BackendProductionGuard(FlowGuardProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean oauth = properties.security().oauth() != null && properties.security().oauth().enabled();
        boolean apiKey = properties.security().apiKeyEnabled();
        if (!oauth && !apiKey) {
            throw new IllegalStateException(
                    "Production backend requires FLOWGUARD_OAUTH_ENABLED=true and/or FLOWGUARD_API_KEY");
        }
        if (properties.gateway() != null && properties.gateway().enabled()) {
            String token = properties.gateway().internalToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException(
                        "When FLOWGUARD_API_GATEWAY_URL is set, GATEWAY_INTERNAL_TOKEN is required on the backend");
            }
        }
        if (properties.fixture() != null) {
            String fixtureToken = properties.fixture().internalToken();
            if (fixtureToken == null || fixtureToken.isBlank()) {
                throw new IllegalStateException(
                        "Production requires FIXTURE_INTERNAL_TOKEN for fixture /internal/** access");
            }
        }
    }
}
