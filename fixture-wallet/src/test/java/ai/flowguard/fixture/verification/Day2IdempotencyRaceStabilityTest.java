package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.harness.ConcurrentCreditRaceRunner;
import ai.flowguard.fixture.service.FixtureResetService;
import ai.flowguard.fixture.service.PaymentLedgerService;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 2 / G1 gate: 20 fresh-state reruns must reproduce PAYMENT_EFFECT_ONCE (I4) violation.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("day2-it")
class Day2IdempotencyRaceStabilityTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final long AMOUNT_MINOR = 10_000L;
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
    PaymentLedgerService paymentLedgerService;

    @Autowired
    FixtureResetService resetService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void idempotencyRaceReproducesI4ViolationTwentyTimesOnFreshState() throws Exception {
        FinancialOracle oracle = new FinancialOracle(jdbcTemplate);
        List<Map<String, Object>> runLog = new ArrayList<>();
        int violations = 0;

        for (int run = 1; run <= REQUIRED_VIOLATIONS; run++) {
            resetService.resetToSeed();
            String key = "day2-idem-" + run;

            ConcurrentCreditRaceRunner.runDuplicateCreditRace(paymentLedgerService, CUSTOMER, key, AMOUNT_MINOR);

            OracleVerdict verdict = oracle.verify(InvariantId.PAYMENT_EFFECT_ONCE);
            long credited = oracle.sumCreditsForKey(key);
            Integer paymentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE idempotency_key = ?", Integer.class, key);

            boolean reproduced = !verdict.passed() && credited == AMOUNT_MINOR * 2 && paymentCount != null && paymentCount >= 2;
            if (reproduced) {
                violations++;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("run", run);
            entry.put("idempotencyKey", key);
            entry.put("oraclePassed", verdict.passed());
            entry.put("creditedMinor", credited);
            entry.put("paymentCount", paymentCount);
            entry.put("reproduced", reproduced);
            runLog.add(entry);
        }

        Day2ReplayEvidenceWriter.write(runLog, violations, REQUIRED_VIOLATIONS);

        assertThat(violations)
                .as("Day 2 requires %d/%d I4 violations on fresh DB state", REQUIRED_VIOLATIONS, REQUIRED_VIOLATIONS)
                .isEqualTo(REQUIRED_VIOLATIONS);
    }
}
