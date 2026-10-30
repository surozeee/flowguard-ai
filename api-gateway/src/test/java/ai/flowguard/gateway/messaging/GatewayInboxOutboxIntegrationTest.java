package ai.flowguard.gateway.messaging;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"gateway-store", "gateway-it"})
class GatewayInboxOutboxIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("flowguard")
            .withUsername("flowguard")
            .withPassword("flowguard")
            .withInitScript("gateway-it/postgres-init.sql");

    static MockWebServer partner;

    @LocalServerPort
    int gatewayPort;

    @Autowired
    GatewayInboxOutboxRepository repository;

    WebTestClient webTestClient;

    @BeforeAll
    static void startPartner() throws IOException {
        partner = new MockWebServer();
        partner.start();
    }

    @AfterAll
    static void stopPartner() throws IOException {
        if (partner != null) {
            partner.shutdown();
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=gateway");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("flowguard.gateway.messaging.backend-base-url", () -> "http://localhost:" + partner.getPort());
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + partner.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/v1/**");
        registry.add("spring.cloud.gateway.routes[0].id", () -> "test-partner");
    }

    @org.junit.jupiter.api.BeforeEach
    void setupClient() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + gatewayPort).build();
    }

    @Test
    void recordsInboxAndPartnerOutboxOnApiCall() {
        partner.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"UP\"}"));

        webTestClient.get()
                .uri("/api/v1/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();

        await().untilAsserted(() -> {
            Map<String, Object> summary = repository.summary();
            assertThat(summary.get("inboxOpenCount")).isEqualTo(0);
            assertThat(((Number) summary.get("partnerOutboxPendingCount")).intValue()).isGreaterThanOrEqualTo(0);
            assertThat(repository.listInbox(5)).isNotEmpty();
            assertThat(repository.listOutbox(5)).isNotEmpty();
        });
    }
}
