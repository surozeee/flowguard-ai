package ai.flowguard.gateway.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@ConditionalOnProperty(name = "flowguard.production.guards-enabled", havingValue = "true")
public class GatewayProductionGuard implements ApplicationRunner {

    private final GatewayMessagingProperties messagingProperties;

    public GatewayProductionGuard(GatewayMessagingProperties messagingProperties) {
        this.messagingProperties = messagingProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String token = messagingProperties.internalToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Production gateway requires GATEWAY_INTERNAL_TOKEN (flowguard.gateway.messaging.internal-token)");
        }
        if (token.length() < 16) {
            throw new IllegalStateException("GATEWAY_INTERNAL_TOKEN must be at least 16 characters in production");
        }
    }
}
