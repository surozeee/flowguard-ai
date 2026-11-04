package ai.flowguard.e2e;

import ai.flowguard.LedgerGuardServiceApplication;
import ai.flowguard.day1.ApiEnvelope;
import ai.flowguard.day1.Day1Infrastructure;
import ai.flowguard.fixture.FixtureWalletApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Production-hardening E2E: Postgres + Redis + Kafka, fixture messaging/idempotency, backend consumers, investigation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("dockerAvailable")
class ProductionStackIntegrationTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    static boolean dockerAvailable() {
        return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }

    private ConfigurableApplicationContext fixtureContext;
    private ConfigurableApplicationContext backendContext;
    private String fixtureBaseUrl;
    private String backendBaseUrl;
    private RestClient restClient;

    @BeforeAll
    void startFullStack() {
        Day1Infrastructure.startAll();

        fixtureContext = E2EStackSupport.startFixture(true);
        fixtureBaseUrl = E2EStackSupport.baseUrl(fixtureContext);

        backendContext = E2EStackSupport.startBackend(
                fixtureBaseUrl,
                E2EStackSupport.BackendOptions.open());
        backendBaseUrl = "http://localhost:" + port(backendContext);
        E2EStackSupport.awaitBackendKafkaListeners(backendContext);
        restClient = RestClient.create();
    }

    @AfterAll
    void shutdown() {
        if (backendContext != null) {
            backendContext.close();
        }
        if (fixtureContext != null) {
            fixtureContext.close();
        }
        Day1Infrastructure.stopAll();
    }

    @Test
    @Order(1)
    void actuatorReadinessAndCustomHealth() {
        @SuppressWarnings("unchecked")
        Map<String, Object> readiness = restClient.get()
                .uri(backendBaseUrl + "/actuator/health/readiness")
                .retrieve()
                .body(Map.class);
        assertThat(readiness.get("status")).isIn("UP", "OUT_OF_SERVICE");

        @SuppressWarnings("unchecked")
        Map<String, Object> healthEnvelope = restClient.get()
                .uri(backendBaseUrl + "/api/v1/health")
                .retrieve()
                .body(Map.class);
        Map<String, Object> health = ApiEnvelope.dataAsMap(healthEnvelope);
        assertThat(health.get("status")).isEqualTo("UP");
    }

    @Test
    @Order(2)
    void customerTransactionIdempotencyAndKafkaProjection() {
        restClient.post().uri(fixtureBaseUrl + "/internal/reset").retrieve().toBodilessEntity();

        Map<String, Object> credit = Map.of(
                "clientReference", "prod-e2e",
                "idempotencyKey", "prod-e2e-idem",
                "targetAccountId", CUSTOMER.toString(),
                "amountMinor", 15_000);

        restClient.post()
                .uri(fixtureBaseUrl + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credit)
                .retrieve()
                .toBodilessEntity();

        restClient.post()
                .uri(fixtureBaseUrl + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credit)
                .retrieve()
                .toBodilessEntity();

        awaitFixtureOutboxDrained();

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = restClient.get()
                    .uri(backendBaseUrl + "/api/v1/customers/idempotency-keys/lookup?key=prod-e2e-idem")
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = ApiEnvelope.dataAsMap(envelope);
            assertThat(data.get("eventType")).isIn("PAYMENT_SETTLED", "PAYMENT_IDEMPOTENT_REPLAY");
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> riskEnvelope = restClient.get()
                .uri(backendBaseUrl + "/api/v1/customers/" + CUSTOMER + "/transaction-risk")
                .retrieve()
                .body(Map.class);
        Map<String, Object> risk = ApiEnvelope.dataAsMap(riskEnvelope);
        assertThat(((Number) risk.get("settledTxnCount")).intValue()).isEqualTo(1);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> traceEnvelope = restClient.get()
                    .uri(backendBaseUrl + "/api/v1/sagas/trace/idempotency/prod-e2e-idem")
                    .retrieve()
                    .body(Map.class);
            java.util.List<Map<String, Object>> trace = ApiEnvelope.dataAsList(traceEnvelope);
            assertThat(trace).isNotEmpty();
            java.util.List<String> eventTypes = trace.stream()
                    .map(row -> String.valueOf(row.get("event_type")))
                    .toList();
            assertThat(eventTypes).contains("SAGA_STARTED");
            assertThat(eventTypes).anyMatch(t -> t.equals("SAGA_COMPLETED") || t.equals("SAGA_IDEMPOTENT_REPLAY"));
        });
    }

    @Test
    @Order(3)
    void debitTransferSagaAndIdempotency() {
        restClient.post().uri(fixtureBaseUrl + "/internal/reset").retrieve().toBodilessEntity();

        UUID source = CUSTOMER;
        UUID target = UUID.fromString("00000000-0000-4000-8000-000000000001");
        restClient.post()
                .uri(fixtureBaseUrl + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "clientReference", "prefund-debit-e2e",
                        "idempotencyKey", "prefund-debit-e2e",
                        "targetAccountId", source.toString(),
                        "amountMinor", 50_000))
                .retrieve()
                .toBodilessEntity();

        Map<String, Object> transfer = Map.of(
                "sourceAccountId", source.toString(),
                "targetAccountId", target.toString(),
                "amountMinor", 5_000);

        restClient.post()
                .uri(fixtureBaseUrl + "/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "prod-e2e-debit")
                .body(transfer)
                .retrieve()
                .toBodilessEntity();

        restClient.post()
                .uri(fixtureBaseUrl + "/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "prod-e2e-debit")
                .body(transfer)
                .retrieve()
                .toBodilessEntity();

        awaitFixtureOutboxDrained();

        await().atMost(90, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> traceEnvelope = restClient.get()
                    .uri(backendBaseUrl + "/api/v1/sagas/trace/idempotency/prod-e2e-debit")
                    .retrieve()
                    .body(Map.class);
            var trace = ApiEnvelope.dataAsList(traceEnvelope);
            assertThat(trace).isNotEmpty();
            var eventTypes = trace.stream()
                    .map(row -> String.valueOf(row.get("event_type")))
                    .toList();
            assertThat(eventTypes).contains("SAGA_STARTED");
            assertThat(eventTypes).anyMatch(t -> t.equals("SAGA_COMPLETED") || t.equals("SAGA_IDEMPOTENT_REPLAY"));
        });
    }

    @Test
    @Order(4)
    void backendTransactionApiCreditReplayAndIntegrationSnapshot() {
        restClient.post().uri(fixtureBaseUrl + "/internal/reset").retrieve().toBodilessEntity();
        String key = "api-txn-credit-" + UUID.randomUUID();
        Map<String, Object> credit = Map.of(
                "clientReference", "api-txn",
                "idempotencyKey", key,
                "targetAccountId", CUSTOMER.toString(),
                "amountMinor", 12_000);

        @SuppressWarnings("unchecked")
        Map<String, Object> firstEnvelope = restClient.post()
                .uri(backendBaseUrl + "/api/v1/transactions/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credit)
                .retrieve()
                .body(Map.class);
        Map<String, Object> first = ApiEnvelope.dataAsMap(firstEnvelope);
        Object paymentId = ((Map<?, ?>) first.get("fixture")).get("paymentId");
        assertThat(paymentId).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> replayEnvelope = restClient.post()
                .uri(backendBaseUrl + "/api/v1/transactions/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credit)
                .retrieve()
                .body(Map.class);
        Map<String, Object> replay = ApiEnvelope.dataAsMap(replayEnvelope);
        assertThat(((Map<?, ?>) replay.get("fixture")).get("paymentId")).isEqualTo(paymentId);

        await().atMost(45, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> snapEnvelope = restClient.get()
                    .uri(backendBaseUrl + "/api/v1/transactions/integration/" + key
                            + "?accountId=" + CUSTOMER)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> snap = ApiEnvelope.dataAsMap(snapEnvelope);
            assertThat(snap.get("idempotencyKey")).isEqualTo(key);
            assertThat(snap.get("choreography")).isInstanceOf(java.util.List.class);
        });
    }

    @Test
    @Order(5)
    void investigationPipelineWithEvidenceAndEvents() {
        Map<String, Object> startBody = Map.of(
                "projectId", "sample-wallet",
                "objective", "Production E2E idempotency probe",
                "maxProbes", 3);

        @SuppressWarnings("unchecked")
        Map<String, Object> acceptedEnvelope = restClient.post()
                .uri(backendBaseUrl + "/api/v1/investigations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(startBody)
                .retrieve()
                .body(Map.class);
        String investigationId = ApiEnvelope.dataAsMap(acceptedEnvelope).get("id").toString();

        await().atMost(180, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> invEnvelope = restClient.get()
                    .uri(backendBaseUrl + "/api/v1/investigations/" + investigationId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> inv = ApiEnvelope.dataAsMap(invEnvelope);
            assertThat(inv.get("state")).isNotIn("CREATED", "QUEUED");
            assertThat(inv.get("state")).isIn("VIOLATION_FOUND", "COMPLETED", "FAILED");
            if ("FAILED".equals(inv.get("state"))) {
                throw new AssertionError("Investigation failed: " + inv.get("errorCode"));
            }
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> eventsEnvelope = restClient.get()
                .uri(backendBaseUrl + "/api/v1/investigations/" + investigationId + "/event-log")
                .retrieve()
                .body(Map.class);
        assertThat(ApiEnvelope.dataAsList(eventsEnvelope)).isNotEmpty();
    }

    private void awaitFixtureOutboxDrained() {
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = restClient.get()
                    .uri(fixtureBaseUrl + "/internal/outbox/summary")
                    .retrieve()
                    .body(Map.class);
            assertThat(((Number) summary.get("sagaPendingCount")).intValue()).isZero();
            assertThat(((Number) summary.get("transactionPendingCount")).intValue()).isZero();
        });
    }

    private static Map<String, Object> fixtureProperties() {
        return Map.of(
                "server.port", 0,
                "DATASOURCE_URL", Day1Infrastructure.fixtureJdbcUrl(),
                "spring.datasource.url", Day1Infrastructure.fixtureJdbcUrl(),
                "spring.datasource.username", Day1Infrastructure.POSTGRES.getUsername(),
                "spring.datasource.password", Day1Infrastructure.POSTGRES.getPassword(),
                "spring.data.redis.host", Day1Infrastructure.REDIS.getHost(),
                "spring.data.redis.port", Day1Infrastructure.REDIS.getMappedPort(6379),
                "spring.kafka.bootstrap-servers", Day1Infrastructure.KAFKA.getBootstrapServers());
    }

    private static Map<String, Object> backendProperties(String fixtureUrl) {
        return Map.of(
                "server.port", 0,
                "spring.datasource.url", Day1Infrastructure.metadataJdbcUrl(),
                "spring.datasource.username", Day1Infrastructure.POSTGRES.getUsername(),
                "spring.datasource.password", Day1Infrastructure.POSTGRES.getPassword(),
                "spring.data.redis.host", Day1Infrastructure.REDIS.getHost(),
                "spring.data.redis.port", Day1Infrastructure.REDIS.getMappedPort(6379),
                "spring.kafka.bootstrap-servers", Day1Infrastructure.KAFKA.getBootstrapServers(),
                "flowguard.fixture.base-url", fixtureUrl,
                "flowguard.meta.stub-when-no-key", true);
    }

    private static int port(ConfigurableApplicationContext context) {
        Integer p = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertThat(p).isNotNull();
        return p;
    }
}
