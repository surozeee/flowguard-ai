package ai.flowguard.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    KeyResolver clientAddressKeyResolver() {
        return exchange -> {
            var address = exchange.getRequest().getRemoteAddress();
            if (address == null || address.getAddress() == null) {
                return Mono.just("unknown");
            }
            return Mono.just(address.getAddress().getHostAddress());
        };
    }
}
