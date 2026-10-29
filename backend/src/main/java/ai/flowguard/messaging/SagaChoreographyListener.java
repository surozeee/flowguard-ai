package ai.flowguard.messaging;

import ai.flowguard.saga.SagaChoreographyService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SagaChoreographyListener {

    private final SagaChoreographyService sagaChoreographyService;

    public SagaChoreographyListener(SagaChoreographyService sagaChoreographyService) {
        this.sagaChoreographyService = sagaChoreographyService;
    }

    @KafkaListener(
            topics = "${flowguard.kafka.topics.saga-events}",
            containerFactory = "sagaKafkaListenerContainerFactory")
    public void onSagaEvent(Map<String, Object> payload) {
        sagaChoreographyService.onSagaEvent(payload);
    }
}
