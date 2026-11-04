package ai.flowguard.day1;

import ai.flowguard.LedgerGuardServiceApplication;
import ai.flowguard.e2e.E2EStackSupport;
import ai.flowguard.fixture.FixtureWalletApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Day 1 gate: Gradle build + real PostgreSQL (both schemas), fixture wallet, backend orchestrator,
 * Redis and Kafka wiring, Flyway migrations, seed reset, payment persist, API smoke.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("dockerAvailable")
class Day1FullStackIntegrationTest {

    @SuppressWarnings("unused")
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static final UUID CUSTOMER_ACCOUNT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private ConfigurableApplicationContext fixtureContext;
    private ConfigurableApplicationContext backendContext;
    private String fixtureBaseUrl;
    private String backendBaseUrl;
    private RestClient restClient;

    @BeforeAll
    void startInfrastructureAndApplications() {
        Day1Infrastructure.startAll();

        Map<String, Object> fixtureProps = Day1StackSettings.fixtureWallet().toSpringProperties();
        fixtureContext = new SpringApplicationBuilder(FixtureWalletApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("day1-it", "day1-stack-it", E2EStackSupport.INTEGRATION_SERVLET_PROFILE)
                .properties(fixtureProps)
                .run(Day1Infrastructure.commandLineArgs(fixtureProps));

        fixtureBaseUrl = "http://localhost:" + requiredPort(fixtureContext);

        Map<String, Object> backendProps = Day1StackSettings.backend(fixtureBaseUrl).toSpringProperties();
        backendContext = new SpringApplicationBuilder(LedgerGuardServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("day1-it", E2EStackSupport.INTEGRATION_SERVLET_PROFILE)
                .properties(backendProps)
                .run(Day1Infrastructure.commandLineArgs(backendProps));

        backendBaseUrl = "http://localhost:" + requiredPort(backendContext);
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(300_000);
        restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @AfterAll
    void shutdownApplications() {
        if (backendContext != null) {
            backendContext.close();
        }
        if (fixtureContext != null) {
            fixtureContext.close();
        }
    }

    @Test
    @Order(1)
    void fixtureHealthAndFlywaySchema() {
        @SuppressWarnings("unchecked")
        Map<String, Object> health = restClient.get()
                .uri(fixtureBaseUrl + "/health")
                .retrieve()
                .body(Map.class);
        assertThat(health.get("status")).isEqualTo("UP");

        Integer tables = Day1Jdbc.get().queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'fixture' AND table_name IN ('accounts','payments','journals','journal_lines')
                """,
                Integer.class);
        assertThat(tables).isEqualTo(4);
    }

    @Test
    @Order(2)
    void fixtureResetAndCreditPersistsToPostgres() {
        restClient.post()
                .uri(fixtureBaseUrl + "/internal/reset")
                .retrieve()
                .toBodilessEntity();

        Integer accounts = Day1Jdbc.get().queryForObject(
                "SELECT COUNT(*) FROM fixture.accounts", Integer.class);
        assertThat(accounts).isEqualTo(2);

        Map<String, Object> body = Map.of(
                "clientReference", "day1-ref-001",
                "idempotencyKey", "day1-idem-001",
                "targetAccountId", CUSTOMER_ACCOUNT.toString(),
                "amountMinor", 10000);

        ResponseEntity<Map> paymentResponse = restClient.post()
                .uri(fixtureBaseUrl + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(Map.class);

        assertThat(paymentResponse.getStatusCode().is2xxSuccessful()).isTrue();

        Long balance = Day1Jdbc.get().queryForObject(
                "SELECT current_balance_minor FROM fixture.accounts WHERE id = ?",
                Long.class,
                CUSTOMER_ACCOUNT);
        assertThat(balance).isEqualTo(10000L);

        Integer paymentRows = Day1Jdbc.get().queryForObject(
                "SELECT COUNT(*) FROM fixture.payments WHERE idempotency_key = 'day1-idem-001'",
                Integer.class);
        assertThat(paymentRows).isEqualTo(1);
    }

    @Test
    @Order(3)
    void backendMetadataFlywayAndProjectsApi() {
        Integer tables = Day1Jdbc.get().queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'metadata'
                  AND table_name IN ('projects','investigations','experiments','hypotheses','run_evidence')
                """,
                Integer.class);
        assertThat(tables).isEqualTo(5);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectsEnvelope = restClient.get()
                .uri(backendBaseUrl + "/api/v1/projects")
                .retrieve()
                .body(Map.class);

        var projects = ApiEnvelope.dataAsList(projectsEnvelope);
        assertThat(projects).isNotEmpty();
        assertThat(projects.getFirst().get("slug")).isEqualTo("sample-wallet");
    }

    @Test
    @Order(4)
    void backendHealthReportsPostgresRedisAndKafka() {
        @SuppressWarnings("unchecked")
        Map<String, Object> healthEnvelope = restClient.get()
                .uri(backendBaseUrl + "/api/v1/health")
                .retrieve()
                .body(Map.class);

        Map<String, Object> health = ApiEnvelope.dataAsMap(healthEnvelope);
        assertThat(health).containsEntry("status", "UP");
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) health.get("dependencies");
        assertThat(deps.get("postgres")).isEqualTo("UP");
        assertThat(deps.get("redis")).isEqualTo("UP");
        assertThat(deps.get("kafka")).isNotNull();
    }

    @Test
    @Order(5)
    void investigationPipelineReachesOracleVerdict() {
        Map<String, Object> startBody = Map.of(
                "projectId", "sample-wallet",
                "objective", "Day 1 integration — duplicate credit idempotency probe",
                "maxProbes", 10);

        @SuppressWarnings("unchecked")
        Map<String, Object> acceptedEnvelope = restClient.post()
                .uri(backendBaseUrl + "/api/v1/investigations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(startBody)
                .retrieve()
                .body(Map.class);

        Map<String, Object> accepted = ApiEnvelope.dataAsMap(acceptedEnvelope);
        assertThat(accepted).containsKey("id");
        String investigationId = accepted.get("id").toString();

        await().atMost(90, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> invEnvelope = restClient.get()
                    .uri(backendBaseUrl + "/api/v1/investigations/" + investigationId)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> inv = ApiEnvelope.dataAsMap(invEnvelope);
            assertThat(inv.get("state")).isIn("VIOLATION_FOUND", "COMPLETED", "FAILED");
            if ("FAILED".equals(inv.get("state"))) {
                throw new AssertionError("Investigation failed: " + inv.get("errorCode"));
            }
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> experimentsEnvelope = restClient.get()
                .uri(backendBaseUrl + "/api/v1/investigations/" + investigationId + "/experiments")
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> experiments = ApiEnvelope.dataAsList(experimentsEnvelope);

        assertThat(experiments).isNotEmpty();
        assertThat(experiments.getFirst().get("oracleVerdict"))
                .isIn("VIOLATION_REPRODUCED", "NO_VIOLATION_OBSERVED");
    }

    private static int requiredPort(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertThat(port).isNotNull();
        return port;
    }
}
