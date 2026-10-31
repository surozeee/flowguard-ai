package ai.flowguard.api;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.ops.FixtureOpsClient;
import ai.flowguard.ops.GatewayOpsClient;
import ai.flowguard.saga.SagaChoreographyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Operations", description = "Inbox/outbox, gateway partner calls, saga health")
@CustomLedgerRestController("/api/v1/ops")
@PreAuthorize("hasAuthority('SCOPE_flowguard:ops') or hasRole('OPS')")
public class OpsController {

    private final FixtureOpsClient fixtureOpsClient;
    private final GatewayOpsClient gatewayOpsClient;
    private final SagaChoreographyRepository sagaChoreographyRepository;

    public OpsController(
            FixtureOpsClient fixtureOpsClient,
            GatewayOpsClient gatewayOpsClient,
            SagaChoreographyRepository sagaChoreographyRepository) {
        this.fixtureOpsClient = fixtureOpsClient;
        this.gatewayOpsClient = gatewayOpsClient;
        this.sagaChoreographyRepository = sagaChoreographyRepository;
    }

    @Operation(summary = "Unified processing health (gateway inbox, partner outbox, fixture outbox, consumer inbox)")
    @GetMapping("/processing/summary")
    public GlobalResponse<Map<String, Object>> processingSummary() {
        Map<String, Object> body = new HashMap<>();
        body.put("gateway", gatewayOpsClient.summary());
        body.put("fixtureOutbox", fixtureOpsClient.outboxSummary());
        body.put("consumerInbox", Map.of(
                "sagaEventCount", sagaChoreographyRepository.countEvents()));
        return GlobalResponse.success(body);
    }

    @Operation(summary = "Outbox DLQ counts and pending (fixture)")
    @GetMapping("/outbox/summary")
    public GlobalResponse<Map<String, Object>> outboxSummary() {
        return GlobalResponse.success(fixtureOpsClient.outboxSummary());
    }

    @GetMapping("/outbox/saga-pending")
    public GlobalResponse<List<Map<String, Object>>> sagaPending(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(fixtureOpsClient.sagaPending(Math.min(limit, 200)));
    }

    @GetMapping("/outbox/transaction-pending")
    public GlobalResponse<List<Map<String, Object>>> transactionPending(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(fixtureOpsClient.transactionPending(Math.min(limit, 200)));
    }

    @Operation(summary = "Saga event DLQ rows")
    @GetMapping("/outbox/saga-dlq")
    public GlobalResponse<List<Map<String, Object>>> sagaDlq(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(fixtureOpsClient.sagaDlq(Math.min(limit, 200)));
    }

    @Operation(summary = "Transaction event DLQ rows")
    @GetMapping("/outbox/transaction-dlq")
    public GlobalResponse<List<Map<String, Object>>> transactionDlq(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(fixtureOpsClient.transactionDlq(Math.min(limit, 200)));
    }

    @PostMapping("/outbox/saga-dlq/{id}/replay")
    public GlobalResponse<Map<String, Object>> replaySagaDlq(@PathVariable UUID id) {
        return GlobalResponse.success(fixtureOpsClient.replaySagaDlq(id));
    }

    @PostMapping("/outbox/transaction-dlq/{id}/replay")
    public GlobalResponse<Map<String, Object>> replayTransactionDlq(@PathVariable UUID id) {
        return GlobalResponse.success(fixtureOpsClient.replayTransactionDlq(id));
    }

    @GetMapping("/gateway/summary")
    public GlobalResponse<Map<String, Object>> gatewaySummary() {
        return GlobalResponse.success(gatewayOpsClient.summary());
    }

    @GetMapping("/gateway/inbox")
    public GlobalResponse<List<Map<String, Object>>> gatewayInbox(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(gatewayOpsClient.inbox(Math.min(limit, 200)));
    }

    @GetMapping("/gateway/outbox")
    public GlobalResponse<List<Map<String, Object>>> gatewayPartnerOutbox(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(gatewayOpsClient.partnerOutbox(Math.min(limit, 200)));
    }

    @GetMapping("/gateway/outbox/dlq")
    public GlobalResponse<List<Map<String, Object>>> gatewayPartnerDlq(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(gatewayOpsClient.partnerOutboxDlq(Math.min(limit, 200)));
    }

    @PostMapping("/gateway/outbox/dlq/{id}/replay")
    public GlobalResponse<Map<String, Object>> replayGatewayPartnerDlq(@PathVariable UUID id) {
        return GlobalResponse.success(gatewayOpsClient.replayPartnerDlq(id));
    }

    @GetMapping("/inbox/saga-events")
    public GlobalResponse<List<Map<String, Object>>> consumerSagaInbox(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(sagaChoreographyRepository.findRecent(Math.min(limit, 200)));
    }

    @GetMapping("/sagas/stale")
    public GlobalResponse<List<Map<String, Object>>> staleSagas(
            @RequestParam(defaultValue = "5") int olderThanMinutes) {
        return GlobalResponse.success(fixtureOpsClient.staleSagas(olderThanMinutes));
    }

    @GetMapping("/sagas/choreography-gaps")
    public GlobalResponse<List<Map<String, Object>>> choreographyGaps(@RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.success(sagaChoreographyRepository.findIncompleteChoreography(Math.min(limit, 200)));
    }
}
