package ai.flowguard.fixture.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentLedgerService ledger;

    public PaymentService(PaymentLedgerService ledger) {
        this.ledger = ledger;
    }

    @Transactional
    public UUID creditPayment(String clientReference, String idempotencyKey, UUID targetAccountId, long amountMinor) {
        rejectNonPositive(amountMinor);
        rejectBlankKey(idempotencyKey);
        return ledger.credit(clientReference, idempotencyKey, targetAccountId, amountMinor);
    }

    @Transactional
    public UUID debitTransfer(UUID sourceAccountId, UUID targetAccountId, long amountMinor, String idempotencyKey) {
        rejectNonPositive(amountMinor);
        rejectBlankKey(idempotencyKey);
        if (sourceAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException("source and target account must differ");
        }
        return ledger.debit(sourceAccountId, targetAccountId, amountMinor, idempotencyKey);
    }

    private static void rejectNonPositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private static void rejectBlankKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotency key is required");
        }
    }
}