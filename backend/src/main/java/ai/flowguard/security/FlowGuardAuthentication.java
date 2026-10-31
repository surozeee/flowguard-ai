package ai.flowguard.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

public class FlowGuardAuthentication extends AbstractAuthenticationToken {

    private final String principal;

    public FlowGuardAuthentication(String principal, Collection<SimpleGrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
