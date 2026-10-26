package ai.flowguard.fixture.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FixtureProperties.class)
public class AppConfig {
}
