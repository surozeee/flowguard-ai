package ai.flowguard.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"gateway-store", "gateway-it"})
class GatewayInternalTokenIntegrationTest {

    private static final String TOKEN = "gateway-internal-test-token";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("flowguard")
            .withUsername("flowguard")
            .withPassword("flowguard")
            .withInitScript("gateway-it/postgres-init.sql");

    @LocalServerPort
    int gatewayPort;

    WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=gateway");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("flowguard.gateway.messaging.internal-token", () -> TOKEN);
    }

    @BeforeEach
    void setupClient() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + gatewayPort).build();
    }

    @Test
    void internalGatewayRequiresTokenWhenConfigured() {
        webTestClient.get()
                .uri("/internal/gateway/summary")
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get()
                .uri("/internal/gateway/summary")
                .header("X-Gateway-Internal-Token", TOKEN)
                .exchange()
                .expectStatus().isOk();
    }
}
