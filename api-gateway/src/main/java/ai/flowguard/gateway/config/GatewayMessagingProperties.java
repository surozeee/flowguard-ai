package ai.flowguard.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowguard.gateway.messaging")
public record GatewayMessagingProperties(
        boolean enabled,
        String partnerId,
        int maxBodyChars,
        int maxPartnerAttempts,
        long partnerRetryDelayMs,
        String internalToken,
        String backendBaseUrl
) {
    public GatewayMessagingProperties {
        if (maxBodyChars <= 0) {
            maxBodyChars = 32_768;
        }
        if (maxPartnerAttempts <= 0) {
            maxPartnerAttempts = 5;
        }
        if (partnerRetryDelayMs <= 0) {
            partnerRetryDelayMs = 2_000;
        }
        if (backendBaseUrl == null || backendBaseUrl.isBlank()) {
            backendBaseUrl = "http://localhost:8080";
        }
    }
}
