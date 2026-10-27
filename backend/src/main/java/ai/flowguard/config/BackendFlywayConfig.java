package ai.flowguard.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackendFlywayConfig {

    @Bean
    FlywayConfigurationCustomizer metadataSchemaOnly() {
        return (FluentConfiguration configuration) -> configuration
                .schemas("metadata")
                .defaultSchema("metadata")
                .locations("classpath:db/migration/metadata");
    }
}
