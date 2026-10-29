package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.service.FixtureResetService;
import ai.flowguard.fixture.service.PaymentService;
import ai.flowguard.oracles.FinancialOracle;
import ai.flowguard.oracles.InvariantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Negative control: reference-safe variant must not reproduce duplicate credit under sequential retries.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("day2-safe-it")
class Day2ReferenceSafeControlTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("flowguard")
            .withUsername("flowguard")
            .withPassword("flowguard")
            .withInitScript("day1/postgres-init.sql");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=fixture");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    PaymentService paymentService;

    @Autowired
    FixtureResetService resetService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void sequentialRetriesWithSameKeyCreditOnce() {
        resetService.resetToSeed();
        String key = "safe-key-001";
        paymentService.creditPayment("safe", key, CUSTOMER, 10_000L);
        paymentService.creditPayment("safe", key, CUSTOMER, 10_000L);

        FinancialOracle oracle = new FinancialOracle(jdbcTemplate);
        assertThat(oracle.verify(InvariantId.PAYMENT_EFFECT_ONCE).passed()).isTrue();
        assertThat(oracle.sumCreditsForKey(key)).isEqualTo(10_000L);
    }
}
