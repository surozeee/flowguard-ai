package ai.flowguard.oracles;

import ai.flowguard.oracles.support.PostgresOracleTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FinancialOracleNegativeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("flowguard")
            .withUsername("flowguard")
            .withPassword("flowguard");

    JdbcTemplate jdbc;
    FinancialOracle oracle;

    @BeforeEach
    void setUp() {
        jdbc = PostgresOracleTestSupport.jdbc(postgres);
        oracle = new FinancialOracle(jdbc);
        PostgresOracleTestSupport.seedCustomerAccount(jdbc);
    }

    @Test
    void duplicateIdempotencyKeyFailsPaymentEffectOnce() {
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fixture.payments (id, client_reference, idempotency_key, target_account_id, amount_minor, currency, status)
                VALUES (?, 'a', 'dup-key', ?, 10000, 'USD', 'SETTLED'),
                       (?, 'b', 'dup-key', ?, 10000, 'USD', 'SETTLED')
                """, paymentA, PostgresOracleTestSupport.CUSTOMER, paymentB, PostgresOracleTestSupport.CUSTOMER);

        OracleVerdict verdict = oracle.verify(InvariantId.PAYMENT_EFFECT_ONCE);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.violations()).anyMatch(v -> v.invariantId() == InvariantId.PAYMENT_EFFECT_ONCE);
    }

    @Test
    void unbalancedJournalFailsJournalBalanced() {
        UUID journalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fixture.journals (id, payment_id, event_type, currency, posting_status)
                VALUES (?, NULL, 'CREDIT', 'USD', 'POSTED')
                """, journalId);
        jdbc.update("""
                INSERT INTO fixture.journal_lines (id, journal_id, account_id, signed_amount_minor, currency)
                VALUES (?, ?, ?, 10000, 'USD')
                """, UUID.randomUUID(), journalId, PostgresOracleTestSupport.CUSTOMER);

        OracleVerdict verdict = oracle.verify(InvariantId.JOURNAL_BALANCED);
        assertThat(verdict.passed()).isFalse();
    }

    @Test
    void balanceDriftFailsAccountReconciles() {
        jdbc.update("UPDATE fixture.accounts SET current_balance_minor = 5000 WHERE id = ?",
                PostgresOracleTestSupport.CUSTOMER);

        OracleVerdict verdict = oracle.verify(InvariantId.ACCOUNT_RECONCILES);
        assertThat(verdict.passed()).isFalse();
    }
}
