package ai.flowguard.fixture.payment.payload.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreditRequest(
        @NotBlank String clientReference,
        String idempotencyKey,
        @NotNull UUID targetAccountId,
        @Min(1) long amountMinor
) {
}
