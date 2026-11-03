package ai.flowguard.benchmarks.baseline;

import ai.flowguard.fixture.service.FixtureResetService;
import ai.flowguard.fixture.service.PaymentLedgerService;
import ai.flowguard.fixture.harness.ConcurrentCreditRaceRunner;
import ai.flowguard.oracles.FinancialOracle;
import ai.flowguard.oracles.InvariantId;
import ai.flowguard.oracles.OracleVerdict;
import ai.flowguard.fixture.FixtureWalletApplication;
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
 * Day 3 frozen baseline: idempotency race should reproduce I4 on every run in this harness.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = FixtureWalletApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("day2-it")
class BaselineIdempotencyBenchmarkTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final int RUNS = 5;

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
    PaymentLedgerService paymentLedgerService;

    @Autowired
    FixtureResetService resetService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void baselineIdempotencyRaceReproducesViolationConsistently() throws Exception {
        FinancialOracle oracle = new FinancialOracle(jdbcTemplate);
        int violations = 0;
        for (int run = 1; run <= RUNS; run++) {
            resetService.resetToSeed();
            String key = "baseline-" + run;
            ConcurrentCreditRaceRunner.runDuplicateCreditRace(paymentLedgerService, CUSTOMER, key, 10_000L);
            OracleVerdict verdict = oracle.verify(InvariantId.PAYMENT_EFFECT_ONCE);
            if (!verdict.passed()) {
                violations++;
            }
        }
        assertThat(violations).isEqualTo(RUNS);
    }
}
