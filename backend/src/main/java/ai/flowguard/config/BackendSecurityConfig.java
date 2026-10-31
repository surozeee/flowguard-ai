package ai.flowguard.config;

import ai.flowguard.security.FlowGuardAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class BackendSecurityConfig {

    private final FlowGuardAuthFilter flowGuardAuthFilter;
    private final FlowGuardProperties properties;

    public BackendSecurityConfig(FlowGuardAuthFilter flowGuardAuthFilter, FlowGuardProperties properties) {
        this.flowGuardAuthFilter = flowGuardAuthFilter;
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (properties.security().requiresAuthentication()) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                            .requestMatchers("/api/v1/health").permitAll()
                            .requestMatchers("/api/v1/ops/**")
                            .hasAnyAuthority("SCOPE_flowguard:ops", "ROLE_OPS")
                            .requestMatchers("/api/v1/**").authenticated()
                            .anyRequest().permitAll())
                    .addFilterBefore(flowGuardAuthFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
