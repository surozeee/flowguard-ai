package ai.flowguard.ops;

import ai.flowguard.config.FlowGuardProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GatewayOpsClient {

    private final RestClient restClient;
    private final FlowGuardProperties properties;

    public GatewayOpsClient(RestClient restClient, FlowGuardProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public Map<String, Object> summary() {
        if (properties.gateway() == null || !properties.gateway().enabled()) {
            return Map.of("enabled", false);
        }
        try {
            Map<String, Object> body = withToken(restClient.get()
                    .uri(properties.gateway().baseUrl() + "/internal/gateway/summary"))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? Map.of("enabled", true, "reachable", false) : body;
        } catch (Exception ex) {
            return Map.of("enabled", true, "reachable", false, "error", ex.getMessage());
        }
    }

    public List<Map<String, Object>> inbox(int limit) {
        return withToken(restClient.get()
                .uri(properties.gateway().baseUrl() + "/internal/gateway/inbox?limit={limit}", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> partnerOutbox(int limit) {
        return withToken(restClient.get()
                .uri(properties.gateway().baseUrl() + "/internal/gateway/outbox?limit={limit}", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> partnerOutboxDlq(int limit) {
        return withToken(restClient.get()
                .uri(properties.gateway().baseUrl() + "/internal/gateway/outbox/dlq?limit={limit}", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> replayPartnerDlq(UUID id) {
        return withToken(restClient.post()
                .uri(properties.gateway().baseUrl() + "/internal/gateway/outbox/dlq/{id}/replay", id))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private RestClient.RequestHeadersSpec<?> withToken(RestClient.RequestHeadersSpec<?> spec) {
        String token = properties.gateway().internalToken();
        if (token != null && !token.isBlank()) {
            return spec.header("X-Gateway-Internal-Token", token);
        }
        return spec;
    }
}
