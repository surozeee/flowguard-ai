package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.idempotency.IdempotencyInProgressException;
import ai.flowguard.fixture.service.FixtureResetService;
import ai.flowguard.fixture.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("redis-it")
class RedisIdempotencyConcurrencyTest {

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

    @Autowired
    PaymentService paymentService;

    @Autowired
    FixtureResetService resetService;

    @Test
    void concurrentSameKeyOneSettlesOthersInProgressOrReplay() throws Exception {
        resetService.resetToSeed();
        String key = "redis-concurrent-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger inProgress = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();
        UUID[] paymentIds = new UUID[2];

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> a = CompletableFuture.runAsync(() -> runOnce(0, key, ready, start, inProgress, success, paymentIds), pool);
            CompletableFuture<Void> b = CompletableFuture.runAsync(() -> runOnce(1, key, ready, start, inProgress, success, paymentIds), pool);
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            CompletableFuture.allOf(a, b).join();
        } finally {
            pool.shutdownNow();
        }

        assertThat(success.get()).isEqualTo(1);
        assertThat(inProgress.get()).isEqualTo(1);

        UUID settled = paymentService.creditPayment("retry", key, CUSTOMER, 10_000L);
        assertThat(settled).isIn(paymentIds[0], paymentIds[1]);
    }

    private void runOnce(
            int slot,
            String key,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger inProgress,
            AtomicInteger success,
            UUID[] paymentIds) {
        ready.countDown();
        try {
            start.await(30, TimeUnit.SECONDS);
            paymentIds[slot] = paymentService.creditPayment("race", key, CUSTOMER, 10_000L);
            success.incrementAndGet();
        } catch (IdempotencyInProgressException ex) {
            inProgress.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
