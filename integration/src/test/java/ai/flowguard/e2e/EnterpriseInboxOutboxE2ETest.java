package ai.flowguard.e2e;

import ai.flowguard.day1.ApiEnvelope;
import ai.flowguard.day1.Day1Infrastructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("dockerAvailable")
class EnterpriseInboxOutboxE2ETest {

    static boolean dockerAvailable() {
        return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }

    private E2EStackSupport.EnterpriseStack stack;
    private RestClient restClient;
    private RestClient opsClient;

    @BeforeAll
    void startEnterpriseStack() {
        Day1Infrastructure.startAll();
        stack = E2EStackSupport.startEnterpriseStack();
        clearGatewayMessagingTables();
        restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", E2EStackSupport.OPS_API_KEY)
                .build();
        opsClient = restClient;
    }

    private void clearGatewayMessagingTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(Day1Infrastructure.gatewayJdbcUrl());
        dataSource.setUsername(Day1Infrastructure.POSTGRES.getUsername());
        dataSource.setPassword(Day1Infrastructure.POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DELETE FROM partner_outbox_dlq");
        jdbc.execute("DELETE FROM partner_outbox");
        jdbc.execute("DELETE FROM api_inbox");
    }

    @AfterAll
    void shutdown() {
        E2EStackSupport.stopQuietly(stack.gateway());
        E2EStackSupport.stopQuietly(stack.backend());
        E2EStackSupport.stopQuietly(stack.fixture());
        Day1Infrastructure.stopAll();
    }

    @Test
    @Order(1)
    void internalGatewayAdminRequiresTokenWhenConfigured() {
        int forbidden = restClient.get()
                .uri(stack.gatewayBaseUrl() + "/internal/gateway/summary")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(forbidden).isEqualTo(403);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = restClient.get()
                .uri(stack.gatewayBaseUrl() + "/internal/gateway/summary")
                .header("X-Gateway-Internal-Token", E2EStackSupport.GATEWAY_INTERNAL_TOKEN)
                .retrieve()
                .body(Map.class);
        assertThat(summary).containsKeys("inboxOpenCount", "partnerOutboxPendingCount");
    }

    @Test
    @Order(2)
    void gatewayRecordsInboxAndPartnerOutboxForHealthCall() {
        restClient.get()
                .uri(stack.gatewayBaseUrl() + "/api/v1/health")
                .retrieve()
                .toBodilessEntity();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = restClient.get()
                    .uri(stack.gatewayBaseUrl() + "/internal/gateway/summary")
                    .header("X-Gateway-Internal-Token", E2EStackSupport.GATEWAY_INTERNAL_TOKEN)
                    .retrieve()
                    .body(Map.class);
            assertThat(((Number) summary.get("inboxFailedCount")).intValue()).isEqualTo(0);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> inbox = restClient.get()
                    .uri(stack.gatewayBaseUrl() + "/internal/gateway/inbox?limit=5")
                    .header("X-Gateway-Internal-Token", E2EStackSupport.GATEWAY_INTERNAL_TOKEN)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            assertThat(inbox).anyMatch(row -> "/api/v1/health".equals(row.get("path")));
        });
    }

    @Test
    @Order(3)
    void backendOpsAggregatesLiveGatewayMetrics() {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> liveGateway = restClient.get()
                    .uri(stack.gatewayBaseUrl() + "/internal/gateway/summary")
                    .header("X-Gateway-Internal-Token", E2EStackSupport.GATEWAY_INTERNAL_TOKEN)
                    .retrieve()
                    .body(Map.class);
            assertThat(liveGateway).containsKeys("inboxOpenCount", "partnerOutboxPendingCount");

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = opsClient.get()
                    .uri(stack.backendBaseUrl() + "/api/v1/ops/processing/summary")
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = ApiEnvelope.dataAsMap(envelope);
            @SuppressWarnings("unchecked")
            Map<String, Object> gateway = (Map<String, Object>) data.get("gateway");
            assertThat(gateway).isNotNull();
            assertThat(gateway.get("enabled")).isEqualTo(true);
        });
    }

    @Test
    @Order(4)
    void paymentFlowsThroughFixtureOutboxToConsumerInbox() {
        restClient.post().uri(stack.fixtureBaseUrl() + "/internal/reset").retrieve().toBodilessEntity();

        UUID customer = UUID.fromString("00000000-0000-4000-8000-000000000002");
        Map<String, Object> credit = Map.of(
                "clientReference", "enterprise-e2e",
                "idempotencyKey", "enterprise-e2e-idem",
                "targetAccountId", customer.toString(),
                "amountMinor", 10_000);

        restClient.post()
                .uri(stack.fixtureBaseUrl() + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credit)
                .retrieve()
                .toBodilessEntity();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> fixtureSummary = restClient.get()
                    .uri(stack.fixtureBaseUrl() + "/internal/outbox/summary")
                    .retrieve()
                    .body(Map.class);
            assertThat(((Number) fixtureSummary.get("sagaPendingCount")).intValue()).isEqualTo(0);

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = restClient.get()
                    .uri(stack.backendBaseUrl() + "/api/v1/sagas/trace/idempotency/enterprise-e2e-idem")
                    .retrieve()
                    .body(Map.class);
            assertThat(ApiEnvelope.dataAsList(envelope)).isNotEmpty();

            @SuppressWarnings("unchecked")
            Map<String, Object> opsEnvelope = opsClient.get()
                    .uri(stack.backendBaseUrl() + "/api/v1/ops/inbox/saga-events?limit=5")
                    .retrieve()
                    .body(Map.class);
            assertThat(ApiEnvelope.dataAsList(opsEnvelope)).isNotEmpty();
        });
    }
}
