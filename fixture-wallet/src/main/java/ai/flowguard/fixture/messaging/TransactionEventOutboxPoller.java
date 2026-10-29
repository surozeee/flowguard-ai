package ai.flowguard.fixture.messaging;

import ai.flowguard.fixture.config.FixtureProperties;
import ai.flowguard.fixture.outbox.OutboxPayloads;
import ai.flowguard.fixture.saga.OutboxPublishSupport;
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
public class TransactionEventOutboxPoller {

    private final TransactionEventOutboxRepository outboxRepository;
    private final KafkaTransactionEventSender kafkaTransactionEventSender;
    private final ObjectMapper objectMapper;
    private final OutboxPublishSupport outboxPublishSupport;
    private final Counter dlqCounter;

    public TransactionEventOutboxPoller(
            TransactionEventOutboxRepository outboxRepository,
            KafkaTransactionEventSender kafkaTransactionEventSender,
            ObjectMapper objectMapper,
            FixtureProperties properties,
            MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTransactionEventSender = kafkaTransactionEventSender;
        this.objectMapper = objectMapper;
        this.outboxPublishSupport = new OutboxPublishSupport(properties.maxOutboxPublishAttempts());
        this.dlqCounter = meterRegistry.counter("flowguard.fixture.transaction_outbox.dlq");
    }

    @Scheduled(fixedDelayString = "${flowguard.fixture.transaction-outbox-poll-ms:500}")
    public void publishPending() {
        for (Map<String, Object> row : outboxRepository.findPending(50)) {
            UUID id = (UUID) row.get("id");
            String partitionKey = (String) row.get("partition_key");
            int attempts = ((Number) row.get("publish_attempts")).intValue();
            try {
                Map<String, Object> payload = OutboxPayloads.parse(objectMapper, row.get("payload_json"));
                kafkaTransactionEventSender.send(partitionKey, payload);
                outboxRepository.markPublished(id);
            } catch (Exception ex) {
                int next = attempts + 1;
                if (outboxPublishSupport.exhausted(next)) {
                    Map<String, Object> payload = OutboxPayloads.parse(objectMapper, row.get("payload_json"));
                    outboxRepository.moveToDlq(id, partitionKey, payload, next, ex.getMessage());
                    dlqCounter.increment();
                } else {
                    outboxRepository.recordFailure(id, next, ex.getMessage());
                }
            }
        }
    }
}
