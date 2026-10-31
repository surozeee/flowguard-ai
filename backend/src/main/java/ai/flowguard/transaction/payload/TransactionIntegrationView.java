package ai.flowguard.transaction.payload;

import ai.flowguard.saga.payload.SagaTraceRow;
import ai.flowguard.saga.payload.SagaVerifyVerdict;

import java.util.List;

public record TransactionIntegrationView(
        String idempotencyKey,
        String accountId,
        CachedTransactionEvent idempotencyRecord,
        CachedTransactionEvent lastTransaction,
        CustomerRiskSnapshot risk,
        List<OrchestratorSagaRow> orchestrator,
        String orchestratorError,
        List<SagaTraceRow> choreography,
        SagaVerifyVerdict verify
) {}
