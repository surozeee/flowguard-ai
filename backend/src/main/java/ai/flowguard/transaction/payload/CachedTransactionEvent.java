package ai.flowguard.transaction.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CachedTransactionEvent(
        String eventType,
        String accountId,
        String idempotencyKey,
        String riskTier
) {}
