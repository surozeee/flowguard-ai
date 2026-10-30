package ai.flowguard.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Configuration
@Profile("!gateway-it")
public class OpaqueTokenIntrospectorConfig {

    @Value("${spring.security.oauth2.resourceserver.opaque-token.introspection-uri}")
    private String introspectionUri;

    @Value("${spring.security.oauth2.resourceserver.opaque-token.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.resourceserver.opaque-token.client-secret}")
    private String clientSecret;

    @Bean
    ReactiveOpaqueTokenIntrospector reactiveOpaqueTokenIntrospector() {
        SpringOpaqueTokenIntrospector delegate =
                new SpringOpaqueTokenIntrospector(introspectionUri, clientId, clientSecret);
        return token -> Mono.fromCallable(() -> delegate.introspect(token))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        OAuth2IntrospectionException.class,
                        ex -> new BadOpaqueTokenException("Token introspection failed", ex));
    }
}
