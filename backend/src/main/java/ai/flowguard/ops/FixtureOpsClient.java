package ai.flowguard.ops;

import ai.flowguard.config.FlowGuardProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FixtureOpsClient {

    private final RestClient restClient;
    private final FlowGuardProperties properties;

    public FixtureOpsClient(RestClient restClient, FlowGuardProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private RestClient.RequestHeadersSpec<?> withInternalToken(RestClient.RequestHeadersSpec<?> spec) {
        String token = properties.fixture().internalToken();
        if (token != null && !token.isBlank()) {
            return spec.header("X-Fixture-Internal-Token", token);
        }
        return spec;
    }

    public Map<String, Object> outboxSummary() {
        try {
            Map<String, Object> body = withInternalToken(restClient.get()
                    .uri(properties.fixture().baseUrl() + "/internal/outbox/summary"))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? Map.of() : body;
        } catch (Exception ex) {
            return Map.of("reachable", false, "error", ex.getMessage());
        }
    }

    public List<Map<String, Object>> sagaPending(int limit) {
        return withInternalToken(restClient.get()
                .uri(properties.fixture().baseUrl() + "/internal/outbox/saga-pending?limit={limit}", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> transactionPending(int limit) {
        return withInternalToken(restClient.get()
                .uri(properties.fixture().baseUrl() + "/internal/outbox/transaction-pending?limit={limit}", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> sagaDlq(int limit) {
        return withInternalToken(restClient.get()
                .uri(properties.fixture().baseUrl() + "/internal/outbox/saga-dlq?limit={limit}", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> transactionDlq(int limit) {
        return withInternalToken(restClient.get()
                .uri(properties.fixture().baseUrl() + "/internal/outbox/transaction-dlq?limit={limit}", limit))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> replaySagaDlq(UUID id) {
        return withInternalToken(restClient.post()
                .uri(properties.fixture().baseUrl() + "/internal/outbox/saga-dlq/{id}/replay", id)
                .contentType(MediaType.APPLICATION_JSON))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> replayTransactionDlq(UUID id) {
        return withInternalToken(restClient.post()
                .uri(properties.fixture().baseUrl() + "/internal/outbox/transaction-dlq/{id}/replay", id)
                .contentType(MediaType.APPLICATION_JSON))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> staleSagas(int olderThanMinutes) {
        return withInternalToken(restClient.get()
                .uri(properties.fixture().baseUrl() + "/internal/sagas/stale?olderThanMinutes={m}", olderThanMinutes))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> sagaRowsByIdempotency(String idempotencyKey) {
        return withInternalToken(restClient.get()
                .uri(properties.fixture().baseUrl() + "/internal/sagas/idempotency/{key}", idempotencyKey))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
