package ai.flowguard.day1;

import ai.flowguard.e2e.E2EStackSupport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed Testcontainers wiring for Day 1 apps. Each JDBC URL is written once as a Spring property
 * (no duplicate SPRING_DATASOURCE_* aliases).
 */
public final class Day1StackSettings {

    private static final String CUSTOMER_ACCOUNT = "00000000-0000-4000-8000-000000000002";

    private Day1StackSettings() {}

    public static FixtureWalletSettings fixtureWallet() {
        return new FixtureWalletSettings(
                Day1Infrastructure.fixtureJdbcUrl(),
                Day1Infrastructure.POSTGRES.getUsername(),
                Day1Infrastructure.POSTGRES.getPassword(),
                Day1Infrastructure.REDIS.getHost(),
                Day1Infrastructure.REDIS.getMappedPort(6379),
                Day1Infrastructure.KAFKA.getBootstrapServers());
    }

    public static BackendSettings backend(String fixtureBaseUrl) {
        return new BackendSettings(
                Day1Infrastructure.metadataJdbcUrl(),
                Day1Infrastructure.fixtureJdbcUrl(),
                Day1Infrastructure.POSTGRES.getUsername(),
                Day1Infrastructure.POSTGRES.getPassword(),
                Day1Infrastructure.REDIS.getHost(),
                Day1Infrastructure.REDIS.getMappedPort(6379),
                Day1Infrastructure.KAFKA.getBootstrapServers(),
                fixtureBaseUrl);
    }

    public record FixtureWalletSettings(
            String jdbcUrl,
            String username,
            String password,
            String redisHost,
            int redisPort,
            String kafkaBootstrap
    ) {
        public Map<String, Object> toSpringProperties() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("server.port", 0);
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", username);
            props.put("spring.datasource.password", password);
            props.put("spring.datasource.hikari.connection-init-sql", "SET search_path TO fixture");
            props.put("spring.data.redis.host", redisHost);
            props.put("spring.data.redis.port", redisPort);
            props.put("spring.kafka.bootstrap-servers", kafkaBootstrap);
            props.put("spring.flyway.locations", "classpath:db/migration/fixture");
            props.put("spring.flyway.schemas", "fixture");
            props.put("spring.flyway.default-schema", "fixture");
            E2EStackSupport.isolateFixtureApp(props);
            return props;
        }
    }

    public record BackendSettings(
            String metadataJdbcUrl,
            String fixtureJdbcUrl,
            String username,
            String password,
            String redisHost,
            int redisPort,
            String kafkaBootstrap,
            String fixtureBaseUrl
    ) {
        public Map<String, Object> toSpringProperties() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("server.port", 0);
            props.put("spring.datasource.url", metadataJdbcUrl);
            props.put("spring.datasource.username", username);
            props.put("spring.datasource.password", password);
            props.put("DATASOURCE_URL", fixtureJdbcUrl);
            props.put("spring.data.redis.host", redisHost);
            props.put("spring.data.redis.port", redisPort);
            props.put("spring.kafka.bootstrap-servers", kafkaBootstrap);
            props.put("flowguard.kafka.messaging-enabled", true);
            props.put("flowguard.fixture.base-url", fixtureBaseUrl);
            props.put("flowguard.fixture.customer-account-id", CUSTOMER_ACCOUNT);
            props.put("flowguard.investigation.sync-pipeline", true);
            props.put("flowguard.meta.stub-when-no-key", true);
            props.put("spring.flyway.locations", "classpath:db/migration/metadata");
            props.put("spring.flyway.schemas", "metadata");
            props.put("spring.flyway.default-schema", "metadata");
            E2EStackSupport.isolateServletApp(props);
            return props;
        }
    }
}
