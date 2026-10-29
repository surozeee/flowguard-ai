package ai.flowguard.fixture.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(15)
public class InternalAccessFilter extends OncePerRequestFilter {

    private final String internalToken;

    public InternalAccessFilter(@Value("${flowguard.fixture.internal-token:}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (internalToken == null || internalToken.isBlank()) {
            return true;
        }
        return !request.getRequestURI().startsWith("/internal");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader("X-Fixture-Internal-Token");
        if (internalToken.equals(provided)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write("""
                {"status":"ERROR","message":"Forbidden","data":{"code":"FORBIDDEN"}}
                """.getBytes(StandardCharsets.UTF_8));
    }
}
