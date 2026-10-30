package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.service.FixtureResetService;
import ai.flowguard.fixture.service.PaymentLedgerService;
import ai.flowguard.fixture.service.PaymentService;
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
@ActiveProfiles("day2-safe-it")
class PaymentLedgerReversalTest {

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
    PaymentLedgerService paymentLedgerService;

    @Autowired
    FixtureResetService resetService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void reverseIfSettledRestoresBalance() {
        resetService.resetToSeed();
        Long before = jdbcTemplate.queryForObject(
                "SELECT current_balance_minor FROM accounts WHERE id = ?", Long.class, CUSTOMER);
        UUID paymentId = paymentService.creditPayment("rev", "rev-key-1", CUSTOMER, 5_000L);
        Long afterCredit = jdbcTemplate.queryForObject(
                "SELECT current_balance_minor FROM accounts WHERE id = ?", Long.class, CUSTOMER);
        assertThat(afterCredit).isEqualTo(before + 5_000L);

        paymentLedgerService.reverseIfSettled(paymentId);

        Long afterReverse = jdbcTemplate.queryForObject(
                "SELECT current_balance_minor FROM accounts WHERE id = ?", Long.class, CUSTOMER);
        assertThat(afterReverse).isEqualTo(before);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE id = ?", String.class, paymentId);
        assertThat(status).isEqualTo("REVERSED");
    }
}
