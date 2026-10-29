package ai.flowguard.fixture.saga;

import ai.flowguard.fixture.config.FixtureProperties;
import ai.flowguard.fixture.outbox.OutboxPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.messaging-enabled", matchIfMissing = true)
public class SagaEventOutboxPoller {

    private final SagaEventOutboxRepository outboxRepository;
    private final KafkaSagaEventSender kafkaSagaEventSender;
    private final ObjectMapper objectMapper;
    private final OutboxPublishSupport outboxPublishSupport;
    private final Counter dlqCounter;

    public SagaEventOutboxPoller(
            SagaEventOutboxRepository outboxRepository,
            KafkaSagaEventSender kafkaSagaEventSender,
            ObjectMapper objectMapper,
            FixtureProperties properties,
            MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaSagaEventSender = kafkaSagaEventSender;
        this.objectMapper = objectMapper;
        this.outboxPublishSupport = new OutboxPublishSupport(properties.maxOutboxPublishAttempts());
        this.dlqCounter = meterRegistry.counter("flowguard.fixture.saga_outbox.dlq");
    }

    @Scheduled(fixedDelayString = "${flowguard.fixture.saga-outbox-poll-ms:500}")
    public void publishPending() {
        for (Map<String, Object> row : outboxRepository.findPending(50)) {
            UUID id = (UUID) row.get("id");
            int attempts = ((Number) row.get("publish_attempts")).intValue();
            try {
                Map<String, Object> payload = OutboxPayloads.parse(objectMapper, row.get("payload_json"));
                kafkaSagaEventSender.send(payload);
                outboxRepository.markPublished(id);
            } catch (Exception ex) {
                int next = attempts + 1;
                if (outboxPublishSupport.exhausted(next)) {
                    Map<String, Object> payload = OutboxPayloads.parse(objectMapper, row.get("payload_json"));
                    String idempotencyKey = row.get("idempotency_key") == null ? null : row.get("idempotency_key").toString();
                    outboxRepository.moveToDlq(id, idempotencyKey, payload, next, ex.getMessage());
                    dlqCounter.increment();
                } else {
                    outboxRepository.recordFailure(id, next, ex.getMessage());
                }
            }
        }
    }
}
