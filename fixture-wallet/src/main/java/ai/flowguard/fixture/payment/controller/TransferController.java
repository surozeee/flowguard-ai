package ai.flowguard.fixture.payment.controller;

import ai.flowguard.fixture.core.web.CustomFixtureRestController;
import ai.flowguard.fixture.payment.payload.request.TransferRequest;
import ai.flowguard.fixture.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.UUID;

@CustomFixtureRestController("/transfers")
public class TransferController {

    private final PaymentService paymentService;

    public TransferController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {
        String idempotencyKey = PaymentController.resolveIdempotencyKey(idempotencyHeader, request.idempotencyKey());
        UUID id = paymentService.debitTransfer(
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amountMinor(),
                idempotencyKey);
        return ResponseEntity.ok(Map.of("paymentId", id));
    }
}
