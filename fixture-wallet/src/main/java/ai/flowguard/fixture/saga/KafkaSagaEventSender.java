package ai.flowguard.fixture.saga;

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
public class KafkaSagaEventSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FixtureProperties properties;
    private final ObjectMapper objectMapper;

    public KafkaSagaEventSender(
            KafkaTemplate<String, Object> kafkaTemplate,
            FixtureProperties properties,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void send(Map<String, Object> event) throws Exception {
        Object key = event.get("idempotencyKey");
        if (key == null) {
            key = event.get("sagaId");
        }
        if (key == null) {
            key = "saga-event";
        }
        Map<String, Object> payload = objectMapper.convertValue(event, new TypeReference<>() {});
        kafkaTemplate.send(properties.sagaEventsTopic(), key.toString(), new LinkedHashMap<>(payload))
                .get(30, TimeUnit.SECONDS);
    }
}
