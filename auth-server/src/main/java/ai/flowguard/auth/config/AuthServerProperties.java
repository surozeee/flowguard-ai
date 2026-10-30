package ai.flowguard.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowguard.auth")
public record AuthServerProperties(
        String issuer,
        String introspectionClientId,
        String introspectionClientSecret,
        String uiClientId,
        String uiClientSecret
) {
}
