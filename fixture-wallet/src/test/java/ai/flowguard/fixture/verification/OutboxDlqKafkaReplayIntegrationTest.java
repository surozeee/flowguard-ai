package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.outbox.OutboxDlqRepository;
import ai.flowguard.fixture.messaging.KafkaTransactionEventSender;
import ai.flowguard.fixture.saga.KafkaSagaEventSender;
import ai.flowguard.fixture.saga.SagaEventOutboxPoller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("day2-safe-it")
class OutboxDlqKafkaReplayIntegrationTest {

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
        registry.add("flowguard.fixture.messaging-enabled", () -> "true");
        registry.add("flowguard.fixture.max-outbox-publish-attempts", () -> "1");
    }

    @MockBean
    KafkaSagaEventSender kafkaSagaEventSender;

    @MockBean
    KafkaTransactionEventSender kafkaTransactionEventSender;

    @Autowired
    SagaEventOutboxPoller sagaEventOutboxPoller;

    @Autowired
    OutboxDlqRepository outboxDlqRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void dlqReplayThenPublishToKafka() throws Exception {
        UUID outboxId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO saga_event_outbox (id, idempotency_key, payload_json, publish_attempts)
                VALUES (?, ?, ?::jsonb, 0)
                """, outboxId, "kafka-replay-key", "{\"eventType\":\"SAGA_STARTED\",\"sagaId\":\"00000000-0000-4000-8000-000000000099\"}");

        doThrow(new RuntimeException("kafka down")).when(kafkaSagaEventSender).send(any());

        sagaEventOutboxPoller.publishPending();

        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saga_event_outbox WHERE id = ?", Integer.class, outboxId);
        assertThat(pending).isZero();

        Integer dlqCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saga_event_outbox_dlq WHERE idempotency_key = ?", Integer.class, "kafka-replay-key");
        assertThat(dlqCount).isEqualTo(1);

        UUID dlqId = jdbcTemplate.queryForObject(
                "SELECT id FROM saga_event_outbox_dlq WHERE idempotency_key = ?", UUID.class, "kafka-replay-key");
        UUID requeuedId = outboxDlqRepository.requeueSagaFromDlq(dlqId);

        org.mockito.Mockito.reset(kafkaSagaEventSender);
        sagaEventOutboxPoller.publishPending();

        verify(kafkaSagaEventSender, times(1)).send(any(Map.class));
        verifyNoMoreInteractions(kafkaSagaEventSender);

        Integer published = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saga_event_outbox WHERE id = ? AND published_at IS NOT NULL",
                Integer.class, requeuedId);
        assertThat(published).isEqualTo(1);
    }
}
