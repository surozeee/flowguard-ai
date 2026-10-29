package ai.flowguard.messaging;

import ai.flowguard.investigation.service.EvidenceCacheService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EvidenceProcessedListener {

    private final EvidenceCacheService evidenceCacheService;

    public EvidenceProcessedListener(EvidenceCacheService evidenceCacheService) {
        this.evidenceCacheService = evidenceCacheService;
    }

    @KafkaListener(
            topics = "${flowguard.kafka.topics.evidence-processed}",
            containerFactory = "evidenceKafkaListenerContainerFactory")
    public void onProcessed(Map<String, Object> payload) {
        evidenceCacheService.storeProcessedEvidence(payload);
    }
}

