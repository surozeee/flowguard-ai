package ai.flowguard.gateway.messaging;

import ai.flowguard.gateway.config.GatewayMessagingProperties;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PartnerOutboxRetryScheduler {

    private final GatewayMessagingProperties properties;
    private final GatewayInboxOutboxRepository repository;
    private final GatewayMessagingMetrics metrics;
    private final WebClient webClient;

    public PartnerOutboxRetryScheduler(
            GatewayMessagingProperties properties,
            GatewayInboxOutboxRepository repository,
            GatewayMessagingMetrics metrics,
            WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.repository = repository;
        this.metrics = metrics;
        this.webClient = webClientBuilder.build();
    }

    @Scheduled(fixedDelayString = "${flowguard.gateway.messaging.partner-retry-delay-ms:5000}")
    public void retryFailedPartnerCalls() {
        if (!properties.enabled()) {
            return;
        }
        List<Map<String, Object>> rows = repository.findRetryableOutbox(10);
        for (Map<String, Object> row : rows) {
            UUID outboxId = (UUID) row.get("id");
            int attempt = ((Number) row.get("attempt_count")).intValue() + 1;
            if (attempt > properties.maxPartnerAttempts()) {
                repository.moveOutboxToDlq(outboxId, "Max partner attempts exceeded");
                metrics.recordPartnerDlq();
                continue;
            }
            String method = String.valueOf(row.get("http_method"));
            String url = String.valueOf(row.get("target_url"));
            String body = row.get("request_body") == null ? "" : row.get("request_body").toString();
            repository.markOutboxDispatching(outboxId, attempt);
            org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec<?> headersSpec =
                    webClient.method(org.springframework.http.HttpMethod.valueOf(method)).uri(url);
            if (body != null && !body.isBlank() && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                headersSpec = ((org.springframework.web.reactive.function.client.WebClient.RequestBodySpec) headersSpec)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body);
            }
            headersSpec.exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(responseBody -> new PartnerResponse(response.statusCode().value(), responseBody)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(partnerResponse -> {
                        if (partnerResponse.status >= 500) {
                            repository.failOutbox(outboxId, "Retry HTTP " + partnerResponse.status);
                            metrics.recordPartnerFailure();
                        } else {
                            repository.succeedOutbox(outboxId, partnerResponse.status, partnerResponse.body);
                            metrics.recordPartnerSuccess();
                        }
                    })
                    .doOnError(err -> {
                        String message = err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage();
                        repository.failOutbox(outboxId, message);
                        metrics.recordPartnerFailure();
                        if (attempt >= properties.maxPartnerAttempts()) {
                            repository.moveOutboxToDlq(outboxId, message);
                            metrics.recordPartnerDlq();
                        }
                    })
                    .subscribe();
        }
    }

    private record PartnerResponse(int status, String body) {
    }
}
