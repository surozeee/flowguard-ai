package ai.flowguard.fixture;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("day1-it")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Day1PostgresIntegrationTest {

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
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void flywayCreatesLedgerTablesAndResetSeedsAccounts() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'fixture'
                  AND table_name IN ('accounts','payments','journals','journal_lines')
                """,
                Integer.class);
        assertThat(tableCount).isEqualTo(4);

        ResponseEntity<Void> reset = restTemplate.postForEntity("/internal/reset", null, Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer accounts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
        assertThat(accounts).isEqualTo(2);
    }

    @Test
    @Order(2)
    void creditPaymentUpdatesBalanceInPostgres() {
        restTemplate.postForEntity("/internal/reset", null, Void.class);

        Map<String, Object> request = Map.of(
                "clientReference", "module-day1",
                "idempotencyKey", "module-day1-key",
                "targetAccountId", CUSTOMER.toString(),
                "amountMinor", 5000);

        ResponseEntity<Map> response = restTemplate.postForEntity("/payments", request, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        Long balance = jdbcTemplate.queryForObject(
                "SELECT current_balance_minor FROM accounts WHERE id = ?",
                Long.class,
                CUSTOMER);
        assertThat(balance).isEqualTo(5000L);
    }
}
