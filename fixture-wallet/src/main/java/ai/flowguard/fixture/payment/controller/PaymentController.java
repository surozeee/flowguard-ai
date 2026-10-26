package ai.flowguard.fixture.payment.controller;

import ai.flowguard.fixture.core.web.CustomFixtureRestController;
import ai.flowguard.fixture.payment.payload.request.CreditRequest;
import ai.flowguard.fixture.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@CustomFixtureRestController("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> credit(
            @Valid @RequestBody CreditRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {
        String idempotencyKey = resolveIdempotencyKey(idempotencyHeader, request.idempotencyKey());
        UUID id = paymentService.creditPayment(
                request.clientReference(),
                idempotencyKey,
                request.targetAccountId(),
                request.amountMinor());
        return ResponseEntity.ok(Map.of("paymentId", id));
    }

    static String resolveIdempotencyKey(String header, String bodyKey) {
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        if (bodyKey != null && !bodyKey.isBlank()) {
            return bodyKey.trim();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header or body idempotencyKey required");
    }
}
