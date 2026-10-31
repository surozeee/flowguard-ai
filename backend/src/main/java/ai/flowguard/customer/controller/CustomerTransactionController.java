package ai.flowguard.customer.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.transaction.TransactionRiskCacheService;
import ai.flowguard.transaction.payload.CachedTransactionEvent;
import ai.flowguard.transaction.payload.CustomerRiskSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@CustomLedgerRestController("/api/v1/customers")
public class CustomerTransactionController {

    private final TransactionRiskCacheService transactionRiskCacheService;

    public CustomerTransactionController(TransactionRiskCacheService transactionRiskCacheService) {
        this.transactionRiskCacheService = transactionRiskCacheService;
    }

    @GetMapping("/{accountId}/transaction-risk")
    public GlobalResponse<CustomerRiskSnapshot> transactionRisk(@PathVariable UUID accountId) {
        return GlobalResponse.success(transactionRiskCacheService.getRiskSnapshot(accountId.toString()));
    }

    @GetMapping("/{accountId}/last-transaction")
    public GlobalResponse<CachedTransactionEvent> lastTransaction(@PathVariable UUID accountId) {
        return GlobalResponse.success(
                transactionRiskCacheService.getLastTransaction(accountId.toString()).orElse(null));
    }

    @GetMapping("/idempotency-keys/lookup")
    public GlobalResponse<CachedTransactionEvent> idempotencyLookup(@RequestParam String key) {
        return GlobalResponse.success(
                transactionRiskCacheService.getIdempotencyRecord(key).orElse(null));
    }
}
