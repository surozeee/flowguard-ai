package ai.flowguard.transaction.payload;

import java.util.Map;
import java.util.UUID;

public record OrchestratorSagaRow(
        UUID id,
        String idempotencyKey,
        String sagaType,
        String state,
        String currentStep,
        UUID accountId,
        UUID paymentId,
        String failureReason,
        Object createdAt,
        Object updatedAt
) {
    public static OrchestratorSagaRow from(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new OrchestratorSagaRow(
                uuid(row.get("id")),
                str(row.get("idempotency_key")),
                str(row.get("saga_type")),
                str(row.get("state")),
                str(row.get("current_step")),
                uuid(row.get("account_id")),
                uuid(row.get("payment_id")),
                str(row.get("failure_reason")),
                row.get("created_at"),
                row.get("updated_at"));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        String raw = value.toString();
        if (raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw);
    }
}
