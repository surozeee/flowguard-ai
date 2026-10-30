package ai.flowguard.auth.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    private final AuthServerProperties properties;

    public AuthorizationServerConfig(AuthServerProperties properties) {
        this.properties = properties;
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/auth/**", "/actuator/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails investigator = User.builder()
                .username("investigator")
                .password(passwordEncoder.encode("investigator123"))
                .roles("INVESTIGATOR")
                .build();
        UserDetails ops = User.builder()
                .username("ops")
                .password(passwordEncoder.encode("ops123"))
                .roles("OPS", "INVESTIGATOR")
                .build();
        return new InMemoryUserDetailsManager(investigator, ops);
    }

    @Bean
    OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        RegisteredClient introspectionClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(properties.introspectionClientId())
                .clientSecret(passwordEncoder.encode(properties.introspectionClientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("internal")
                .build();

        TokenSettings referenceTokens = TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .accessTokenTimeToLive(Duration.ofHours(8))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .build();

        RegisteredClient uiClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(properties.uiClientId())
                .clientSecret(passwordEncoder.encode(properties.uiClientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope("flowguard:read")
                .scope("flowguard:ops")
                .tokenSettings(referenceTokens)
                .build();

        return new InMemoryRegisteredClientRepository(introspectionClient, uiClient);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(properties.issuer())
                .build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtEncodingCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            if (context.getPrincipal().getAuthorities() != null) {
                context.getClaims().claim(
                        "roles",
                        context.getPrincipal().getAuthorities().stream()
                                .map(a -> a.getAuthority().replace("ROLE_", ""))
                                .toList());
            }
        };
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
