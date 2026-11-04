package ai.flowguard.e2e;

import ai.flowguard.day1.ApiEnvelope;
import ai.flowguard.day1.Day1Infrastructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("dockerAvailable")
class OpsStackIntegrationTest {

    static boolean dockerAvailable() {
        return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }

    private ConfigurableApplicationContext fixtureContext;
    private ConfigurableApplicationContext backendContext;
    private String fixtureBaseUrl;
    private String backendBaseUrl;
    private RestClient opsClient;

    @BeforeAll
    void startStack() {
        Day1Infrastructure.startAll();

        fixtureContext = E2EStackSupport.startFixture(true);
        fixtureBaseUrl = E2EStackSupport.baseUrl(fixtureContext);

        backendContext = E2EStackSupport.startBackend(
                fixtureBaseUrl,
                E2EStackSupport.BackendOptions.opsOnly());
        backendBaseUrl = E2EStackSupport.baseUrl(backendContext);
        E2EStackSupport.awaitBackendKafkaListeners(backendContext);

        opsClient = RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", E2EStackSupport.OPS_API_KEY)
                .build();
    }

    @AfterAll
    void shutdown() {
        E2EStackSupport.stopQuietly(backendContext);
        E2EStackSupport.stopQuietly(fixtureContext);
    }

    @Test
    void opsOutboxSummaryRequiresApiKeyAndReturnsCounts() {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = opsClient.get()
                .uri(backendBaseUrl + "/api/v1/ops/outbox/summary")
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = ApiEnvelope.dataAsMap(envelope);
        assertThat(data).containsKeys("sagaDlqCount", "transactionDlqCount", "sagaPendingCount");
    }

    @Test
    void processingSummaryAggregatesGatewayAndFixture() {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = opsClient.get()
                .uri(backendBaseUrl + "/api/v1/ops/processing/summary")
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = ApiEnvelope.dataAsMap(envelope);
        assertThat(data).containsKeys("gateway", "fixtureOutbox", "consumerInbox");
    }

    @Test
    void paymentClearsFixturePendingOutboxAndPopulatesConsumerInbox() {
        restClient().post().uri(fixtureBaseUrl + "/internal/reset").retrieve().toBodilessEntity();

        UUID customer = UUID.fromString("00000000-0000-4000-8000-000000000002");
        Map<String, Object> credit = Map.of(
                "clientReference", "ops-e2e",
                "idempotencyKey", "ops-e2e-idem",
                "targetAccountId", customer.toString(),
                "amountMinor", 12_000);

        restClient().post()
                .uri(fixtureBaseUrl + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credit)
                .retrieve()
                .toBodilessEntity();

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> fixtureSummary = restClient().get()
                    .uri(fixtureBaseUrl + "/internal/outbox/summary")
                    .retrieve()
                    .body(Map.class);
            assertThat(((Number) fixtureSummary.get("transactionPendingCount")).intValue()).isZero();
            assertThat(((Number) fixtureSummary.get("sagaPendingCount")).intValue()).isZero();
        });

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> summaryEnvelope = opsClient.get()
                    .uri(backendBaseUrl + "/api/v1/ops/outbox/summary")
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> summary = ApiEnvelope.dataAsMap(summaryEnvelope);
            assertThat(((Number) summary.get("sagaPendingCount")).intValue()).isEqualTo(0);

            @SuppressWarnings("unchecked")
            Map<String, Object> inboxEnvelope = opsClient.get()
                    .uri(backendBaseUrl + "/api/v1/ops/inbox/saga-events?limit=10")
                    .retrieve()
                    .body(Map.class);
            assertThat(ApiEnvelope.dataAsList(inboxEnvelope)).isNotEmpty();
        });
    }

    private RestClient restClient() {
        return RestClient.create();
    }

}
