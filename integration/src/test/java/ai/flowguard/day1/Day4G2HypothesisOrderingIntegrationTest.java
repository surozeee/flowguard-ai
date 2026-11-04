package ai.flowguard.day1;

import ai.flowguard.LedgerGuardServiceApplication;
import ai.flowguard.e2e.E2EStackSupport;
import ai.flowguard.fixture.FixtureWalletApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnabledIf("dockerAvailable")
class Day4G2HypothesisOrderingIntegrationTest {

    static boolean dockerAvailable() {
        return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }

    private static ConfigurableApplicationContext fixtureContext;
    private static ConfigurableApplicationContext backendContext;
    private static String backendBaseUrl;
    private static RestClient restClient;

    @BeforeAll
    static void startStack() {
        Day1Infrastructure.startAll();
        Map<String, Object> fixtureProps = Day1StackSettings.fixtureWallet().toSpringProperties();
        fixtureProps.put("flowguard.fixture.variant", "dup-credit-vulnerable");
        fixtureContext = new SpringApplicationBuilder(FixtureWalletApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("day1-it", "day2-it", E2EStackSupport.INTEGRATION_SERVLET_PROFILE)
                .properties(fixtureProps)
                .run(Day1Infrastructure.commandLineArgs(fixtureProps));
        String fixtureBaseUrl = "http://localhost:" + fixtureContext.getEnvironment().getProperty("local.server.port");

        Map<String, Object> backendProps = Day1StackSettings.backend(fixtureBaseUrl).toSpringProperties();
        backendContext = new SpringApplicationBuilder(LedgerGuardServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("day1-it", E2EStackSupport.INTEGRATION_SERVLET_PROFILE)
                .properties(backendProps)
                .run(Day1Infrastructure.commandLineArgs(backendProps));
        backendBaseUrl = "http://localhost:" + backendContext.getEnvironment().getProperty("local.server.port");
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(180_000);
        restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @AfterAll
    static void stopStack() {
        if (backendContext != null) {
            backendContext.close();
        }
        if (fixtureContext != null) {
            fixtureContext.close();
        }
        Day1Infrastructure.stopAll();
    }

    @Test
    void hypothesisIsPersistedBeforeExperimentCompletes() {
        Map<String, Object> body = Map.of(
                "projectId", "sample-wallet",
                "objective", "Reproduce duplicate credit under concurrency",
                "maxProbes", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = restClient.post()
                .uri(backendBaseUrl + "/api/v1/investigations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = ApiEnvelope.dataAsMap(envelope);
        UUID investigationId = UUID.fromString(data.get("id").toString());

        await().atMost(90, TimeUnit.SECONDS).untilAsserted(() -> {
            String state = Day1Jdbc.get().queryForObject(
                    "SELECT state FROM metadata.investigations WHERE id = ?",
                    String.class, investigationId);
            assertThat(state).isIn("VIOLATION_FOUND", "COMPLETED", "FAILED");
        });

        Instant hypothesisAt = Day1Jdbc.get().queryForObject(
                """
                SELECT h.created_at FROM metadata.hypotheses h
                WHERE h.investigation_id = ? ORDER BY h.created_at LIMIT 1
                """,
                Instant.class, investigationId);
        Instant experimentStarted = Day1Jdbc.get().queryForObject(
                """
                SELECT e.started_at FROM metadata.experiments e
                WHERE e.investigation_id = ? ORDER BY e.started_at LIMIT 1
                """,
                Instant.class, investigationId);

        assertThat(hypothesisAt).isBeforeOrEqualTo(experimentStarted);

        Integer eventCount = Day1Jdbc.get().queryForObject(
                "SELECT COUNT(*) FROM metadata.investigation_events WHERE investigation_id = ?",
                Integer.class, investigationId);
        assertThat(eventCount).isGreaterThan(0);
    }
}
