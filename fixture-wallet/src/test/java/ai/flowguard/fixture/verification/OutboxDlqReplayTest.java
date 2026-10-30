package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.outbox.OutboxDlqRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("day2-safe-it")
class OutboxDlqReplayTest {

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
    OutboxDlqRepository outboxDlqRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void replaySagaDlqRequeuesOutboxRow() {
        UUID dlqId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO saga_event_outbox_dlq (id, idempotency_key, payload_json, publish_attempts, last_error)
                VALUES (?, ?, ?::jsonb, 5, 'simulated failure')
                """, dlqId, "dlq-replay-key", "{\"eventType\":\"SAGA_STARTED\"}");

        UUID outboxId = outboxDlqRepository.requeueSagaFromDlq(dlqId);

        Integer dlqCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saga_event_outbox_dlq WHERE id = ?", Integer.class, dlqId);
        assertThat(dlqCount).isZero();

        Map<String, Object> pending = jdbcTemplate.queryForMap(
                "SELECT id, idempotency_key FROM saga_event_outbox WHERE id = ?", outboxId);
        assertThat(pending.get("idempotency_key")).isEqualTo("dlq-replay-key");
    }

    @Test
    void replayTransactionDlqRequeuesOutboxRow() {
        UUID dlqId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO transaction_event_outbox_dlq (id, partition_key, payload_json, publish_attempts, last_error)
                VALUES (?, ?, ?::jsonb, 5, 'simulated failure')
                """, dlqId, "acct-1", "{\"eventType\":\"PAYMENT_SETTLED\"}");

        UUID outboxId = outboxDlqRepository.requeueTransactionFromDlq(dlqId);

        Integer dlqCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_event_outbox_dlq WHERE id = ?", Integer.class, dlqId);
        assertThat(dlqCount).isZero();

        Map<String, Object> pending = jdbcTemplate.queryForMap(
                "SELECT id, partition_key FROM transaction_event_outbox WHERE id = ?", outboxId);
        assertThat(pending.get("partition_key")).isEqualTo("acct-1");
    }
}
