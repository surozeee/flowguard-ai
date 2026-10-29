package ai.flowguard.fixture.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    public PaymentService(PaymentSagaOrchestrator paymentSagaOrchestrator) {
        this.paymentSagaOrchestrator = paymentSagaOrchestrator;
    }

    @Transactional
    public UUID creditPayment(String clientReference, String idempotencyKey, UUID targetAccountId, long amountMinor) {
        return paymentSagaOrchestrator.runCredit(clientReference, idempotencyKey, targetAccountId, amountMinor);
    }

    @Transactional
    public UUID debitTransfer(UUID sourceAccountId, UUID targetAccountId, long amountMinor, String idempotencyKey) {
        return paymentSagaOrchestrator.runDebit(sourceAccountId, targetAccountId, amountMinor, idempotencyKey);
    }
}
