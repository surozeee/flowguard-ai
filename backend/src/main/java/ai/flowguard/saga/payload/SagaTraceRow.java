package ai.flowguard.saga.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record SagaTraceRow(
        UUID id,
        @JsonProperty("saga_id") UUID sagaId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("saga_state") String sagaState,
        String step,
        @JsonProperty("payload_json") Object payloadJson,
        @JsonProperty("recorded_at") Instant recordedAt
) {}
