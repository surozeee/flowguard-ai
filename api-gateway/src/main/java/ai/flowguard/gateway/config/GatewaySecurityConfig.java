package ai.flowguard.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@Profile("!gateway-it")
public class GatewaySecurityConfig {

    private static final String[] PUBLIC = {
            "/actuator/**",
            "/oauth2/**",
            "/api/v1/auth/**",
            "/api/v1/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final ReactiveOpaqueTokenIntrospector introspector;

    public GatewaySecurityConfig(ReactiveOpaqueTokenIntrospector introspector) {
        this.introspector = introspector;
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC).permitAll()
                        .pathMatchers("/api/v1/ops/**").hasAuthority("SCOPE_flowguard:ops")
                        .pathMatchers("/api/v1/**").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(token -> token.introspector(introspector)));
        return http.build();
    }
}
