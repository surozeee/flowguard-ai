package ai.flowguard.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

public final class FlowGuardAuthorities {

    private FlowGuardAuthorities() {
    }

    public static FlowGuardAuthentication apiKeyPrincipal(String name) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("SCOPE_flowguard:read"));
        authorities.add(new SimpleGrantedAuthority("SCOPE_flowguard:ops"));
        authorities.add(new SimpleGrantedAuthority("ROLE_OPS"));
        authorities.add(new SimpleGrantedAuthority("ROLE_INVESTIGATOR"));
        return new FlowGuardAuthentication(name, authorities);
    }
}
