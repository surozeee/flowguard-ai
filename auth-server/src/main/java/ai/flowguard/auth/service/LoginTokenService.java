package ai.flowguard.auth.service;

import ai.flowguard.auth.config.AuthServerProperties;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class LoginTokenService {

    private final AuthenticationManager authenticationManager;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final AuthServerProperties properties;
    private final AuthorizationServerSettings authorizationServerSettings;

    public LoginTokenService(
            AuthenticationManager authenticationManager,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthServerProperties properties,
            AuthorizationServerSettings authorizationServerSettings) {
        this.authenticationManager = authenticationManager;
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.authorizationServerSettings = authorizationServerSettings;
    }

    public TokenResponse login(String username, String password) {
        Authentication userAuth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        RegisteredClient client = registeredClientRepository.findByClientId(properties.uiClientId());
        if (client == null) {
            throw new IllegalStateException("UI OAuth client is not registered");
        }

        Set<String> authorizedScopes = scopesFor(userAuth);
        AuthorizationGrantType grantType = new AuthorizationGrantType("password");

        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return authorizationServerSettings.getIssuer();
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return authorizationServerSettings;
            }
        });
        try {
            OAuth2TokenContext accessTokenContext = DefaultOAuth2TokenContext.builder()
                    .registeredClient(client)
                    .principal(userAuth)
                    .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                    .authorizedScopes(authorizedScopes)
                    .authorizationGrantType(grantType)
                    .authorizationGrant(userAuth)
                    .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                    .build();

            OAuth2Token generatedAccessToken = tokenGenerator.generate(accessTokenContext);
            if (generatedAccessToken == null) {
                throw new IllegalStateException("Failed to generate access token");
            }
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    generatedAccessToken.getTokenValue(),
                    generatedAccessToken.getIssuedAt(),
                    generatedAccessToken.getExpiresAt(),
                    authorizedScopes);

            OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(client)
                    .principalName(userAuth.getName())
                    .authorizationGrantType(grantType)
                    .authorizedScopes(authorizedScopes)
                    .attribute(Authentication.class.getName(), userAuth);

            authorizationBuilder.token(
                    accessToken,
                    metadata -> metadata.put(
                            OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                            Map.of("scope", String.join(" ", authorizedScopes))));

            authorizationService.save(authorizationBuilder.build());

            long expiresIn = accessToken.getExpiresAt() == null
                    ? 28_800
                    : Math.max(0, accessToken.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());

            return new TokenResponse(
                    accessToken.getTokenValue(),
                    "Bearer",
                    expiresIn,
                    String.join(" ", authorizedScopes));
        } finally {
            AuthorizationServerContextHolder.resetContext();
        }
    }

    private static Set<String> scopesFor(Authentication authentication) {
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add("flowguard:read");
        boolean ops = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_OPS".equals(a));
        if (ops) {
            scopes.add("flowguard:ops");
        }
        return scopes;
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {
    }
}
