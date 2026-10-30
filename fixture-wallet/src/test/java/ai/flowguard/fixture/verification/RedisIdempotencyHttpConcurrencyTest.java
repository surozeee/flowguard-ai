package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.service.FixtureResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("redis-it")
class RedisIdempotencyHttpConcurrencyTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("flowguard")
            .withUsername("flowguard")
            .withPassword("flowguard")
            .withInitScript("day1/postgres-init.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=fixture");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    @Autowired
    FixtureResetService resetService;

    @Test
    void httpConcurrentIdempotencyKeyReturns409ForInProgress() throws Exception {
        resetService.resetToSeed();
        String key = "http-concurrent-" + UUID.randomUUID();
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Map<String, Object> body = Map.of(
                "clientReference", "http-race",
                "targetAccountId", CUSTOMER.toString(),
                "amountMinor", 10_000);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> a = CompletableFuture.runAsync(
                    () -> postOnce(client, body, key, ready, start, ok, conflict), pool);
            CompletableFuture<Void> b = CompletableFuture.runAsync(
                    () -> postOnce(client, body, key, ready, start, ok, conflict), pool);
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            CompletableFuture.allOf(a, b).join();
        } finally {
            pool.shutdownNow();
        }

        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);
    }

    private static void postOnce(
            RestClient client,
            Map<String, Object> body,
            String key,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger ok,
            AtomicInteger conflict) {
        ready.countDown();
        try {
            start.await(30, TimeUnit.SECONDS);
            client.post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", key)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            ok.incrementAndGet();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                conflict.incrementAndGet();
            } else {
                throw ex;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
