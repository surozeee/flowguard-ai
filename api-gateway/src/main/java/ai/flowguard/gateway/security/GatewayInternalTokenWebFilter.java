package ai.flowguard.gateway.security;

import ai.flowguard.gateway.config.GatewayMessagingProperties;
import ai.flowguard.gateway.core.GatewayApiEnvelope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GatewayInternalTokenWebFilter implements WebFilter {

    private static final String HEADER = "X-Gateway-Internal-Token";

    private final GatewayMessagingProperties properties;

    public GatewayInternalTokenWebFilter(GatewayMessagingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path == null || !path.startsWith("/internal/gateway")) {
            return chain.filter(exchange);
        }
        String expected = properties.internalToken();
        if (expected == null || expected.isBlank()) {
            return chain.filter(exchange);
        }
        String provided = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (expected.equals(provided)) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = GatewayApiEnvelope.errorJson("Forbidden");
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
