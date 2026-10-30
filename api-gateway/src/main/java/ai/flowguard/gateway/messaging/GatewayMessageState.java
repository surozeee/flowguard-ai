package ai.flowguard.gateway.messaging;

public final class GatewayMessageState {

    public static final String INBOX_RECEIVED = "RECEIVED";
    public static final String INBOX_DISPATCHED = "DISPATCHED";
    public static final String INBOX_COMPLETED = "COMPLETED";
    public static final String INBOX_FAILED = "FAILED";

    public static final String OUTBOX_PENDING = "PENDING";
    public static final String OUTBOX_DISPATCHING = "DISPATCHING";
    public static final String OUTBOX_SUCCESS = "SUCCESS";
    public static final String OUTBOX_FAILED = "FAILED";
    public static final String OUTBOX_DLQ = "DLQ";

    private GatewayMessageState() {
    }
}
