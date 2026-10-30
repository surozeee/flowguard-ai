package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.service.FixtureResetService;
import ai.flowguard.fixture.service.PaymentService;
import ai.flowguard.fixture.risk.RiskEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

/**
 * Post-ledger failure triggers saga compensation including ledger reversal.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("day2-safe-it")
class PaymentSagaCompensationIntegrationTest {

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

    @MockitoSpyBean
    RiskEvaluationService riskEvaluationService;

    @BeforeEach
    void reset() {
        resetService.resetToSeed();
    }

    @Test
    void failedStepAfterLedgerReversesPaymentAndRestoresBalance() {
        Long before = jdbcTemplate.queryForObject(
                "SELECT current_balance_minor FROM accounts WHERE id = ?", Long.class, CUSTOMER);

        doThrow(new RuntimeException("simulated post-ledger failure"))
                .when(riskEvaluationService)
                .recordUsage(org.mockito.ArgumentMatchers.any(), anyLong());

        assertThatThrownBy(() -> paymentService.creditPayment("comp", "comp-key-1", CUSTOMER, 8_000L))
                .isInstanceOf(RuntimeException.class);

        Long after = jdbcTemplate.queryForObject(
                "SELECT current_balance_minor FROM accounts WHERE id = ?", Long.class, CUSTOMER);
        assertThat(after).isEqualTo(before);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE idempotency_key = ?", String.class, "comp-key-1");
        assertThat(status).isEqualTo("REVERSED");
    }
}
