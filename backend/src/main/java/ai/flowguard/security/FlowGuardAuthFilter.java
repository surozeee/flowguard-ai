package ai.flowguard.security;

import ai.flowguard.config.FlowGuardProperties;
import ai.flowguard.core.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(20)
public class FlowGuardAuthFilter extends OncePerRequestFilter {

    private final FlowGuardProperties properties;
    private final BearerTokenIntrospectionClient introspectionClient;

    public FlowGuardAuthFilter(FlowGuardProperties properties, BearerTokenIntrospectionClient introspectionClient) {
        this.properties = properties;
        this.introspectionClient = introspectionClient;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.security().requiresAuthentication()) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (properties.security().apiKeyEnabled() && properties.security().apiKey().equals(apiKey)) {
            SecurityContextHolder.getContext().setAuthentication(
                    FlowGuardAuthorities.apiKeyPrincipal("api-key-client"));
            filterChain.doFilter(request, response);
            return;
        }

        String bearer = bearerToken(request);
        if (bearer != null
                && properties.security().oauth() != null
                && properties.security().oauth().enabled()) {
            var introspected = introspectionClient.introspect(bearer);
            if (introspected.active()) {
                SecurityContextHolder.getContext().setAuthentication(
                        new FlowGuardAuthentication(introspected.subject(), introspected.authorities()));
                filterChain.doFilter(request, response);
                return;
            }
        }

        unauthorized(response, request);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private void unauthorized(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER);
        String body = """
                {"status":"ERROR","message":"Unauthorized","data":{"code":"UNAUTHORIZED","correlationId":"%s"}}
                """.formatted(correlationId == null ? "" : correlationId);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
