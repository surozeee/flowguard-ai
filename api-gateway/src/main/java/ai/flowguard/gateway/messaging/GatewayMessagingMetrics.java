package ai.flowguard.gateway.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayMessagingMetrics {

    private final Counter inboxReceived;
    private final Counter partnerSuccess;
    private final Counter partnerFailure;
    private final Counter partnerDlq;

    public GatewayMessagingMetrics(MeterRegistry registry) {
        this.inboxReceived = registry.counter("flowguard.gateway.inbox.received");
        this.partnerSuccess = registry.counter("flowguard.gateway.partner_outbox.success");
        this.partnerFailure = registry.counter("flowguard.gateway.partner_outbox.failure");
        this.partnerDlq = registry.counter("flowguard.gateway.partner_outbox.dlq");
    }

    public void recordInboxReceived() {
        inboxReceived.increment();
    }

    public void recordPartnerSuccess() {
        partnerSuccess.increment();
    }

    public void recordPartnerFailure() {
        partnerFailure.increment();
    }

    public void recordPartnerDlq() {
        partnerDlq.increment();
    }
}
