package ai.flowguard.messaging;

import ai.flowguard.transaction.TransactionRiskCacheService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TransactionEventListener {

    private final TransactionRiskCacheService transactionRiskCacheService;

    public TransactionEventListener(TransactionRiskCacheService transactionRiskCacheService) {
        this.transactionRiskCacheService = transactionRiskCacheService;
    }

    @KafkaListener(
            topics = "${flowguard.kafka.topics.transactions}",
            containerFactory = "transactionKafkaListenerContainerFactory")
    public void onTransaction(Map<String, Object> payload) {
        transactionRiskCacheService.onTransactionEvent(payload);
    }
}
