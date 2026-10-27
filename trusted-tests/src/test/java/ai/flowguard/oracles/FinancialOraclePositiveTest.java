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
class FinancialOraclePositiveTest {

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
    void balancedSingleCreditPassesAllInvariants() {
        UUID treasuryId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        jdbc.update("""
                INSERT INTO fixture.accounts (id, currency, opening_balance_minor, current_balance_minor, overdraft_allowed, row_version)
                VALUES (?, 'USD', 0, -10000, false, 0)
                ON CONFLICT (id) DO NOTHING
                """, treasuryId);

        UUID paymentId = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fixture.payments (id, client_reference, idempotency_key, target_account_id, amount_minor, currency, status)
                VALUES (?, 'ref', 'key-1', ?, 10000, 'USD', 'SETTLED')
                """, paymentId, PostgresOracleTestSupport.CUSTOMER);
        jdbc.update("""
                INSERT INTO fixture.journals (id, payment_id, event_type, currency, posting_status)
                VALUES (?, ?, 'CREDIT', 'USD', 'POSTED')
                """, journalId, paymentId);
        jdbc.update("""
                INSERT INTO fixture.journal_lines (id, journal_id, account_id, signed_amount_minor, currency)
                VALUES (?, ?, ?, 10000, 'USD'), (?, ?, ?, -10000, 'USD')
                """, UUID.randomUUID(), journalId, PostgresOracleTestSupport.CUSTOMER,
                UUID.randomUUID(), journalId, treasuryId);
        jdbc.update("UPDATE fixture.accounts SET current_balance_minor = 10000 WHERE id = ?",
                PostgresOracleTestSupport.CUSTOMER);
        jdbc.update("UPDATE fixture.accounts SET current_balance_minor = -10000 WHERE id = ?", treasuryId);

        OracleVerdict verdict = oracle.verifyAll();
        assertThat(verdict.passed()).isTrue();
    }
}
