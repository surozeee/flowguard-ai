package ai.flowguard.fixture.messaging;

import ai.flowguard.fixture.config.FixtureProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.messaging-enabled", matchIfMissing = true)
public class KafkaTransactionEventSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FixtureProperties properties;
    private final ObjectMapper objectMapper;

    public KafkaTransactionEventSender(
            KafkaTemplate<String, Object> kafkaTemplate,
            FixtureProperties properties,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void send(String partitionKey, Map<String, Object> event) throws Exception {
        Map<String, Object> payload = objectMapper.convertValue(event, new TypeReference<>() {});
        kafkaTemplate.send(properties.transactionsTopic(), partitionKey, new LinkedHashMap<>(payload))
                .get(30, TimeUnit.SECONDS);
    }
}
