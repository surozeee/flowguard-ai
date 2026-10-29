package ai.flowguard.api;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.ops.FixtureOpsClient;
import ai.flowguard.saga.SagaChoreographyService;
import ai.flowguard.saga.SagaTraceVerifier;
import ai.flowguard.saga.payload.SagaTraceRow;
import ai.flowguard.saga.payload.SagaUnifiedTrace;
import ai.flowguard.saga.payload.SagaVerifyVerdict;
import ai.flowguard.transaction.payload.OrchestratorSagaRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Sagas", description = "Payment saga choreography trace")
@CustomLedgerRestController("/api/v1/sagas")
public class SagaTraceController {

    private final SagaChoreographyService sagaChoreographyService;
    private final SagaTraceVerifier sagaTraceVerifier;
    private final FixtureOpsClient fixtureOpsClient;

    public SagaTraceController(
            SagaChoreographyService sagaChoreographyService,
            SagaTraceVerifier sagaTraceVerifier,
            FixtureOpsClient fixtureOpsClient) {
        this.sagaChoreographyService = sagaChoreographyService;
        this.sagaTraceVerifier = sagaTraceVerifier;
        this.fixtureOpsClient = fixtureOpsClient;
    }

    @Operation(summary = "Choreography trace by idempotency key")
    @GetMapping("/trace/idempotency/{idempotencyKey}")
    public GlobalResponse<List<SagaTraceRow>> traceByIdempotency(@PathVariable String idempotencyKey) {
        return GlobalResponse.success(sagaChoreographyService.traceByIdempotencyKey(idempotencyKey));
    }

    @Operation(summary = "Verify saga choreography completeness")
    @GetMapping("/verify/idempotency/{idempotencyKey}")
    public GlobalResponse<SagaVerifyVerdict> verifyByIdempotency(@PathVariable String idempotencyKey) {
        return GlobalResponse.success(sagaTraceVerifier.verifyWithRetry(idempotencyKey, 3_000));
    }

    @Operation(summary = "Orchestrator (fixture) + choreography (backend) unified view")
    @GetMapping("/unified/idempotency/{idempotencyKey}")
    public GlobalResponse<SagaUnifiedTrace> unifiedTrace(@PathVariable String idempotencyKey) {
        List<OrchestratorSagaRow> orchestrator = fixtureOpsClient.sagaRowsByIdempotency(idempotencyKey).stream()
                .map(OrchestratorSagaRow::from)
                .filter(row -> row != null)
                .toList();
        return GlobalResponse.success(new SagaUnifiedTrace(
                idempotencyKey,
                orchestrator,
                sagaChoreographyService.traceByIdempotencyKey(idempotencyKey),
                sagaTraceVerifier.verify(idempotencyKey)));
    }
}
