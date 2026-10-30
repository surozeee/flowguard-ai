package ai.flowguard.gateway.filter;

import ai.flowguard.gateway.config.GatewayMessagingProperties;
import ai.flowguard.gateway.messaging.GatewayInboxOutboxRepository;
import ai.flowguard.gateway.messaging.GatewayMessageState;
import ai.flowguard.gateway.messaging.GatewayMessagingMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PartnerInboxOutboxGlobalFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final GatewayMessagingProperties properties;
    private final GatewayInboxOutboxRepository repository;
    private final GatewayMessagingMetrics metrics;
    private final ObjectMapper objectMapper;

    public PartnerInboxOutboxGlobalFilter(
            GatewayMessagingProperties properties,
            GatewayInboxOutboxRepository repository,
            GatewayMessagingMetrics metrics,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled() || !shouldTrack(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        if (isBodyLess(request)) {
            return ReactiveSecurityContextHolder.getContext()
                    .map(ctx -> ctx.getAuthentication() != null ? ctx.getAuthentication().getName() : "anonymous")
                    .defaultIfEmpty("anonymous")
                    .flatMap(principal -> process(exchange, chain, request, null, principal))
                    .onErrorResume(ex -> chain.filter(exchange));
        }
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().allocateBuffer(0))
                .flatMap(joined -> ReactiveSecurityContextHolder.getContext()
                        .map(ctx -> ctx.getAuthentication() != null ? ctx.getAuthentication().getName() : "anonymous")
                        .defaultIfEmpty("anonymous")
                        .flatMap(principal -> process(exchange, chain, request, joined, principal)))
                .onErrorResume(ex -> chain.filter(exchange));
    }

    private Mono<Void> process(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            ServerHttpRequest request,
            DataBuffer joined,
            String principal) {
        byte[] bodyBytes = joined == null ? new byte[0] : readBytes(joined);
        if (joined != null) {
            DataBufferUtils.release(joined);
        }

        UUID inboxId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        String correlationId = headerOrNew(request, CORRELATION_HEADER);
        String idempotencyKey = request.getHeaders().getFirst(IDEMPOTENCY_HEADER);
        String path = request.getURI().getRawPath();
        String query = request.getURI().getRawQuery();
        String method = request.getMethod().name();

        Optional<Map<String, Object>> cached = repository.findCompletedInboxByIdempotency(method, path, idempotencyKey);
        if (cached.isPresent()) {
            return writeCachedResponse(exchange, cached.get());
        }
        String clientIp = request.getRemoteAddress() == null ? "unknown"
                : request.getRemoteAddress().getAddress().getHostAddress();
        String requestBody = truncate(new String(bodyBytes, StandardCharsets.UTF_8));
        String targetUrl = properties.backendBaseUrl() + path + (query == null || query.isBlank() ? "" : "?" + query);
        String headersJson = serializeHeaders(request);

        return Mono.fromRunnable(() -> {
                    repository.insertInbox(
                            inboxId,
                            correlationId,
                            method,
                            path,
                            query,
                            principal,
                            clientIp,
                            idempotencyKey,
                            headersJson,
                            requestBody,
                            GatewayMessageState.INBOX_RECEIVED);
                    repository.insertPartnerOutbox(
                            outboxId,
                            inboxId,
                            properties.partnerId(),
                            targetUrl,
                            method,
                            requestBody,
                            GatewayMessageState.OUTBOX_PENDING);
                    repository.markInboxState(inboxId, GatewayMessageState.INBOX_DISPATCHED);
                    repository.markOutboxDispatching(outboxId, 1);
                    metrics.recordInboxReceived();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> {
                    ServerHttpRequest forwardRequest = joined == null
                            ? request
                            : decorateRequest(request, bodyBytes);
                    AtomicBoolean recorded = new AtomicBoolean(false);
                    ServerHttpResponseDecorator responseDecorator = decorateResponse(
                            exchange.getResponse(), inboxId, outboxId, recorded);
                    ServerWebExchange mutated = exchange.mutate()
                            .request(forwardRequest)
                            .response(responseDecorator)
                            .build();
                    return chain.filter(mutated)
                            .then(Mono.defer(() -> {
                                ServerHttpResponse resp = mutated.getResponse();
                                int status = resp.getStatusCode() == null ? 500 : resp.getStatusCode().value();
                                return recordOnce(recorded, status, "", inboxId, outboxId);
                            }))
                            .doOnError(err -> handleFailure(inboxId, outboxId, 1, err));
                }));
    }

    private ServerHttpResponseDecorator decorateResponse(
            ServerHttpResponse response,
            UUID inboxId,
            UUID outboxId,
            AtomicBoolean recorded) {
        return new ServerHttpResponseDecorator(response) {
            {
                beforeCommit(() -> {
                    if (recorded.get()) {
                        return Mono.empty();
                    }
                    int status = getStatusCode() == null ? 500 : getStatusCode().value();
                    return recordOnce(recorded, status, "", inboxId, outboxId);
                });
            }

            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return Flux.from(body)
                        .collectList()
                        .flatMap(buffers -> {
                            int status = getStatusCode() == null ? 500 : getStatusCode().value();
                            if (buffers.isEmpty()) {
                                return recordOnce(recorded, status, "", inboxId, outboxId)
                                        .then(super.writeWith(Flux.empty()));
                            }
                            DataBuffer joined = response.bufferFactory().join(buffers);
                            String responseBody = truncate(new String(readBytes(joined), StandardCharsets.UTF_8));
                            DataBufferUtils.release(joined);
                            return recordOnce(recorded, status, responseBody, inboxId, outboxId)
                                    .then(super.writeWith(Flux.just(
                                            response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8)))));
                        });
            }
        };
    }

    private Mono<Void> recordOnce(
            AtomicBoolean recorded, int status, String responseBody, UUID inboxId, UUID outboxId) {
        if (!recorded.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return recordPartnerResult(status, responseBody, inboxId, outboxId);
    }

    private Mono<Void> recordPartnerResult(int status, String responseBody, UUID inboxId, UUID outboxId) {
        return Mono.fromRunnable(() -> {
            if (status >= 500) {
                repository.failOutbox(outboxId, "Partner HTTP " + status);
                repository.failInbox(inboxId, "Partner HTTP " + status);
                metrics.recordPartnerFailure();
            } else {
                repository.succeedOutbox(outboxId, status, responseBody);
                repository.completeInbox(inboxId, status, responseBody);
                metrics.recordPartnerSuccess();
            }
        });
    }

    private void handleFailure(UUID inboxId, UUID outboxId, int attempt, Throwable err) {
        String message = err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage();
        repository.failOutbox(outboxId, message);
        repository.failInbox(inboxId, message);
        metrics.recordPartnerFailure();
        if (attempt >= properties.maxPartnerAttempts()) {
            repository.moveOutboxToDlq(outboxId, message);
            metrics.recordPartnerDlq();
        }
    }

    private static ServerHttpRequest decorateRequest(ServerHttpRequest request, byte[] bodyBytes) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(HttpHeaders.TRANSFER_ENCODING);
                headers.setContentLength(bodyBytes.length);
                return headers;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance.wrap(bodyBytes));
            }
        };
    }

    private String serializeHeaders(ServerHttpRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getHeaders().toSingleValueMap());
        } catch (Exception e) {
            return "{}";
        }
    }

    private Mono<Void> writeCachedResponse(ServerWebExchange exchange, Map<String, Object> cached) {
        int status = cached.get("response_status") instanceof Number number
                ? number.intValue()
                : HttpStatus.OK.value();
        String body = cached.get("response_body") == null ? "" : cached.get("response_body").toString();
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        metrics.recordInboxReceived();
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private static boolean isBodyLess(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }

    private static boolean shouldTrack(ServerHttpRequest request) {
        String path = request.getURI().getRawPath();
        return path.startsWith("/api/v1/")
                && !path.startsWith("/api/v1/auth/")
                && !path.startsWith("/internal/");
    }

    private static String headerOrNew(ServerHttpRequest request, String name) {
        String value = request.getHeaders().getFirst(name);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        int max = properties.maxBodyChars();
        return body.length() <= max ? body : body.substring(0, max);
    }

    private static byte[] readBytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }
}
