package ai.flowguard.transaction.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.transaction.TransactionCommandService;
import ai.flowguard.transaction.TransactionCommandService.CreditCommand;
import ai.flowguard.transaction.TransactionCommandService.TransferCommand;
import ai.flowguard.transaction.payload.TransactionCommandResult;
import ai.flowguard.transaction.payload.TransactionIntegrationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Tag(name = "Transactions", description = "Create fixture payments and inspect idempotency + saga choreography")
@CustomLedgerRestController("/api/v1/transactions")
public class TransactionController {

    private final TransactionCommandService transactionCommandService;

    public TransactionController(TransactionCommandService transactionCommandService) {
        this.transactionCommandService = transactionCommandService;
    }

    @Operation(summary = "Create an idempotent credit through the fixture wallet")
    @PostMapping("/credits")
    public GlobalResponse<TransactionCommandResult> credit(@Valid @RequestBody CreditRequest request) {
        return GlobalResponse.success(transactionCommandService.credit(new CreditCommand(
                request.clientReference(),
                request.idempotencyKey(),
                request.targetAccountId(),
                request.amountMinor())));
    }

    @Operation(summary = "Create a debit transfer (orchestrated saga + choreography)")
    @PostMapping("/transfers")
    public GlobalResponse<TransactionCommandResult> transfer(@Valid @RequestBody TransferRequest request) {
        return GlobalResponse.success(transactionCommandService.transfer(new TransferCommand(
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amountMinor(),
                request.idempotencyKey(),
                request.prefund(),
                request.fundMinor())));
    }

    @Operation(summary = "Full integration snapshot: Redis idempotency, fixture saga, backend choreography")
    @GetMapping("/integration/{idempotencyKey}")
    public GlobalResponse<TransactionIntegrationView> integration(
            @PathVariable String idempotencyKey,
            @RequestParam(required = false) String accountId) {
        return GlobalResponse.success(transactionCommandService.integration(idempotencyKey, accountId));
    }

    public record CreditRequest(
            String clientReference,
            @NotBlank String idempotencyKey,
            UUID targetAccountId,
            @Min(1) long amountMinor) {}

    public record TransferRequest(
            UUID sourceAccountId,
            UUID targetAccountId,
            @Min(1) long amountMinor,
            @NotBlank String idempotencyKey,
            boolean prefund,
            long fundMinor) {}
}
