package ai.flowguard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(FlowGuardProperties.class)
public class AppConfig {

    @Bean
    RestClientCustomizer restClientTimeouts() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return builder -> builder.requestFactory(factory);
    }

    @Bean
    RestClientCustomizer fixtureInternalToken(FlowGuardProperties properties) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            String token = properties.fixture() == null ? null : properties.fixture().internalToken();
            String base = properties.fixture() == null ? null : properties.fixture().baseUrl();
            String uri = request.getURI().toString();
            if (token != null
                    && !token.isBlank()
                    && base != null
                    && uri.startsWith(base)
                    && request.getURI().getPath() != null
                    && request.getURI().getPath().contains("/internal")) {
                request.getHeaders().set("X-Fixture-Internal-Token", token);
            }
            return execution.execute(request, body);
        });
    }

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "fixtureJdbcTemplate")
    JdbcTemplate fixtureJdbcTemplate(
            @Value("${DATASOURCE_URL:}") String datasourceUrl,
            @Value("${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/flowguard?currentSchema=metadata}")
                    String springDatasourceUrl,
            @Value("${SPRING_DATASOURCE_USERNAME:flowguard}") String username,
            @Value("${SPRING_DATASOURCE_PASSWORD:flowguard}") String password) {
        String url = JdbcUrlGuard.resolveFixtureUrl(datasourceUrl, springDatasourceUrl);
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("fixture-ledger");
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setInitializationFailTimeout(30_000);
        ds.setConnectionTimeout(10_000);
        ds.setValidationTimeout(5_000);
        ds.setConnectionTestQuery("SELECT 1");
        ds.setConnectionInitSql("SET search_path TO fixture");
        return new JdbcTemplate(ds);
    }
}
