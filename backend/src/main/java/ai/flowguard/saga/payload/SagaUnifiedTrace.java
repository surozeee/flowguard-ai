package ai.flowguard.saga.payload;

import ai.flowguard.transaction.payload.OrchestratorSagaRow;

import java.util.List;

public record SagaUnifiedTrace(
        String idempotencyKey,
        List<OrchestratorSagaRow> orchestrator,
        List<SagaTraceRow> choreography,
        SagaVerifyVerdict verify
) {}
