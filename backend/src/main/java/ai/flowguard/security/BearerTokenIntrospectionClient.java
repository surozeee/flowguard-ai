package ai.flowguard.security;

import ai.flowguard.config.FlowGuardProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class BearerTokenIntrospectionClient {

    private final RestClient restClient;
    private final FlowGuardProperties properties;

    public BearerTokenIntrospectionClient(RestClient.Builder builder, FlowGuardProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public IntrospectionResult introspect(String token) {
        FlowGuardProperties.Security.OAuth oauth = properties.security().oauth();
        if (oauth == null || !oauth.enabled()) {
            return IntrospectionResult.inactive();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = restClient.post()
                .uri(oauth.introspectionUri())
                .header(HttpHeaders.AUTHORIZATION, basicAuth(oauth.clientId(), oauth.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (body == null) {
            return IntrospectionResult.inactive();
        }
        boolean active = Boolean.TRUE.equals(body.get("active"));
        if (!active) {
            return IntrospectionResult.inactive();
        }
        String subject = body.get("sub") == null ? "user" : body.get("sub").toString();
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        Object scope = body.get("scope");
        if (scope instanceof String scopeStr) {
            for (String part : scopeStr.split("\\s+")) {
                if (!part.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + part));
                }
            }
        }
        return new IntrospectionResult(true, subject, authorities);
    }

    private static String basicAuth(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public record IntrospectionResult(boolean active, String subject, List<SimpleGrantedAuthority> authorities) {
        static IntrospectionResult inactive() {
            return new IntrospectionResult(false, "", List.of());
        }
    }
}
