package ai.flowguard.auth.api;

import ai.flowguard.auth.service.LoginTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthLoginController {

    private final LoginTokenService loginTokenService;

    public AuthLoginController(LoginTokenService loginTokenService) {
        this.loginTokenService = loginTokenService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        LoginTokenService.TokenResponse token = loginTokenService.login(request.username(), request.password());
        return Map.of(
                "access_token", token.accessToken(),
                "token_type", token.tokenType(),
                "expires_in", token.expiresIn(),
                "scope", token.scope());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
