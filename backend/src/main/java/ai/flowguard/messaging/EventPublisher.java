package ai.flowguard.messaging;

import ai.flowguard.config.FlowGuardProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FlowGuardProperties properties;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, FlowGuardProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publishInvestigation(String investigationId, String eventType, Map<String, Object> payload) {
        kafkaTemplate.send(
                properties.kafka().topics().investigations(),
                investigationId,
                Map.of("eventType", eventType, "investigationId", investigationId, "payload", payload));
    }

    public void publishExperiment(String experimentId, String eventType, Map<String, Object> payload) {
        kafkaTemplate.send(
                properties.kafka().topics().experiments(),
                experimentId,
                Map.of("eventType", eventType, "experimentId", experimentId, "payload", payload));
    }

    public void publishEvidence(String experimentId, Map<String, Object> evidence) {
        kafkaTemplate.send(properties.kafka().topics().evidence(), experimentId, evidence);
    }
}
