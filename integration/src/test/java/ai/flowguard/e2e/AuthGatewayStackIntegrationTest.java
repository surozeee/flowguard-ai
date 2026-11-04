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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("dockerAvailable")
class AuthGatewayStackIntegrationTest {

    static boolean dockerAvailable() {
        return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }

    private ConfigurableApplicationContext fixtureContext;
    private ConfigurableApplicationContext backendContext;
    private ConfigurableApplicationContext authContext;
    private ConfigurableApplicationContext gatewayContext;
    private String gatewayBaseUrl;
    private RestClient restClient;

    @BeforeAll
    void startStack() {
        Day1Infrastructure.startAll();

        fixtureContext = E2EStackSupport.startFixture(true);
        String fixtureUrl = E2EStackSupport.baseUrl(fixtureContext);

        authContext = E2EStackSupport.startAuthServer();
        String authUrl = E2EStackSupport.baseUrl(authContext);

        backendContext = E2EStackSupport.startBackend(
                fixtureUrl,
                E2EStackSupport.BackendOptions.oauth(authUrl + "/oauth2/introspect"));
        String backendUrl = E2EStackSupport.baseUrl(backendContext);

        gatewayContext = E2EStackSupport.startGatewaySecure(
                backendUrl,
                authUrl,
                E2EStackSupport.GATEWAY_INTERNAL_TOKEN);
        gatewayBaseUrl = E2EStackSupport.baseUrl(gatewayContext);
        restClient = RestClient.create();
    }

    @AfterAll
    void shutdown() {
        E2EStackSupport.stopQuietly(gatewayContext);
        E2EStackSupport.stopQuietly(backendContext);
        E2EStackSupport.stopQuietly(authContext);
        E2EStackSupport.stopQuietly(fixtureContext);
        Day1Infrastructure.stopAll();
    }

    @Test
    @Order(1)
    void loginThroughGatewayReturnsBearerToken() {
        Map<String, Object> body = Map.of("username", "investigator", "password", "investigator123");
        @SuppressWarnings("unchecked")
        Map<String, Object> token = restClient.post()
                .uri(gatewayBaseUrl + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        assertThat(token.get("access_token")).isNotNull();
        assertThat(token.get("token_type")).isEqualTo("Bearer");
    }

    @Test
    @Order(2)
    void apiHealthThroughGatewayRequiresBearerToken() {
        int unauthenticated = restClient.get()
                .uri(gatewayBaseUrl + "/api/v1/health")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(unauthenticated).isIn(401, 403);

        String accessToken = loginOpsToken();

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = restClient.get()
                .uri(gatewayBaseUrl + "/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        Map<String, Object> health = ApiEnvelope.dataAsMap(envelope);
        assertThat(health.get("status")).isEqualTo("UP");
    }

    @Test
    @Order(3)
    void opsApiThroughGatewayWithOpsUserToken() {
        String accessToken = loginOpsToken();

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = restClient.get()
                .uri(gatewayBaseUrl + "/api/v1/ops/outbox/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = ApiEnvelope.dataAsMap(envelope);
        assertThat(data).containsKeys("sagaDlqCount", "transactionDlqCount");
    }

    @Test
    @Order(4)
    void investigatorTokenCannotAccessOpsRoutes() {
        Map<String, Object> body = Map.of("username", "investigator", "password", "investigator123");
        @SuppressWarnings("unchecked")
        Map<String, Object> token = restClient.post()
                .uri(gatewayBaseUrl + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        int status = restClient.get()
                .uri(gatewayBaseUrl + "/api/v1/ops/outbox/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get("access_token"))
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isIn(401, 403);
    }

    private String loginOpsToken() {
        Map<String, Object> body = Map.of("username", "ops", "password", "ops123");
        @SuppressWarnings("unchecked")
        Map<String, Object> token = restClient.post()
                .uri(gatewayBaseUrl + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        return token.get("access_token").toString();
    }
}
