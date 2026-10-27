package ai.flowguard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ledgerGuardOpenApi() {
        final String apiKeyScheme = "ApiKeyAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerGuard Orchestrator API")
                        .description("Enterprise investigation, evaluation, and customer transaction projection API.")
                        .version("v1")
                        .contact(new Contact().name("FlowGuard").email("platform@flowguard.local"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(apiKeyScheme, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList(apiKeyScheme));
    }
}
