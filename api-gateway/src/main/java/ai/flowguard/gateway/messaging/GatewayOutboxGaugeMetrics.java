package ai.flowguard.gateway.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GatewayOutboxGaugeMetrics {

    private final GatewayInboxOutboxRepository repository;
    private final AtomicInteger inboxOpen = new AtomicInteger();
    private final AtomicInteger partnerPending = new AtomicInteger();
    private final AtomicInteger partnerDlq = new AtomicInteger();

    public GatewayOutboxGaugeMetrics(GatewayInboxOutboxRepository repository, MeterRegistry registry) {
        this.repository = repository;
        Gauge.builder("flowguard.gateway.inbox.open", inboxOpen, AtomicInteger::get).register(registry);
        Gauge.builder("flowguard.gateway.partner_outbox.pending", partnerPending, AtomicInteger::get).register(registry);
        Gauge.builder("flowguard.gateway.partner_outbox.dlq.count", partnerDlq, AtomicInteger::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${flowguard.gateway.messaging.metrics-poll-ms:5000}")
    public void refresh() {
        Map<String, Object> summary = repository.summary();
        inboxOpen.set(intVal(summary.get("inboxOpenCount")));
        partnerPending.set(intVal(summary.get("partnerOutboxPendingCount")));
        partnerDlq.set(intVal(summary.get("partnerOutboxDlqCount")));
    }

    private static int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
