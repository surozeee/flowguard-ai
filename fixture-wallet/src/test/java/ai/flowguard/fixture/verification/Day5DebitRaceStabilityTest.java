package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.service.FixtureResetService;
import ai.flowguard.fixture.service.PaymentService;
import ai.flowguard.oracles.FinancialOracle;
import ai.flowguard.oracles.InvariantId;
import ai.flowguard.oracles.OracleVerdict;
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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("day5-it")
class Day5DebitRaceStabilityTest {

    private static final UUID TREASURY = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final long FUND_MINOR = 100_000L;
    private static final long DEBIT_MINOR = 60_000L;
    private static final int REQUIRED_VIOLATIONS = 20;

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
    void concurrentDebitRaceReconcilesViolationTwentyTimesOnFreshState() throws Exception {
        FinancialOracle oracle = new FinancialOracle(jdbcTemplate);
        int violations = 0;

        for (int run = 1; run <= REQUIRED_VIOLATIONS; run++) {
            resetService.resetToSeed();
            paymentService.creditPayment("fund-" + run, "fund-key-" + run, CUSTOMER, FUND_MINOR);

            ConcurrentDebitRaceRunner.runConcurrentDebitRace(
                    paymentService,
                    CUSTOMER,
                    TREASURY,
                    DEBIT_MINOR,
                    "debit-a-" + run,
                    "debit-b-" + run);

            OracleVerdict verdict = oracle.verify(InvariantId.ACCOUNT_RECONCILES);
            if (!verdict.passed()) {
                violations++;
            }
        }

        assertThat(violations).isEqualTo(REQUIRED_VIOLATIONS);
    }
}
