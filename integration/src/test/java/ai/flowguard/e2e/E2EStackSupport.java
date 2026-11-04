package ai.flowguard.e2e;

import ai.flowguard.LedgerGuardServiceApplication;
import ai.flowguard.auth.AuthServerApplication;
import ai.flowguard.day1.Day1Infrastructure;
import ai.flowguard.fixture.FixtureWalletApplication;
import ai.flowguard.gateway.ApiGatewayApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public final class E2EStackSupport {

    public static final String INTEGRATION_SERVLET_PROFILE = "integration-servlet";

    public static final String OPS_API_KEY = "test-ops-api-key";
    public static final String GATEWAY_INTERNAL_TOKEN = "e2e-gateway-internal-token";
    public static final String AUTH_INTROSPECT_CLIENT_ID = "flowguard-gateway";
    public static final String AUTH_INTROSPECT_CLIENT_SECRET = "flowguard-gateway-secret";

    /**
     * Integration tests depend on api-gateway (reactive); servlet apps must not load gateway auto-config.
     */
    private static final String SERVLET_EXCLUDE_GATEWAY_AUTOCONFIG =
            "org.springframework.cloud.gateway.config.GatewayAutoConfiguration,"
                    + "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration,"
                    + "org.springframework.cloud.gateway.config.GatewayReactiveLoadBalancerClientAutoConfiguration,"
                    + "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration,"
                    + "org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration,"
                    + "org.springframework.cloud.gateway.config.GatewayNoLoadBalancerClientAutoConfiguration,"
                    + "org.springframework.cloud.gateway.config.SimpleUrlHandlerMappingGlobalCorsAutoConfiguration,"
                    + "org.springframework.cloud.gateway.config.GatewayFunctionRouteAutoConfiguration";

    private E2EStackSupport() {
    }

    private static final String FIXTURE_EXTRA_AUTOCONFIG_EXCLUDE =
            ",org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                    + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration";

    public static void isolateServletApp(Map<String, Object> props) {
        props.put("spring.autoconfigure.exclude", SERVLET_EXCLUDE_GATEWAY_AUTOCONFIG);
        props.put("spring.cloud.gateway.enabled", false);
        props.put("spring.main.web-application-type", "servlet");
        // Backend application.yml is on the integration test classpath; fixture/auth must not inherit its health groups.
        props.put("management.endpoint.health.validate-group-membership", false);
    }

    public static void isolateFixtureApp(Map<String, Object> props) {
        props.put(
                "spring.autoconfigure.exclude",
                SERVLET_EXCLUDE_GATEWAY_AUTOCONFIG + FIXTURE_EXTRA_AUTOCONFIG_EXCLUDE);
        props.put("spring.cloud.gateway.enabled", false);
        props.put("management.endpoint.health.validate-group-membership", false);
        props.put("flowguard.fixture.internal-token", "");
    }

    public static int port(ConfigurableApplicationContext context) {
        Integer p = context.getEnvironment().getProperty("local.server.port", Integer.class);
        if (p == null) {
            throw new IllegalStateException("local.server.port not set on " + context.getId());
        }
        return p;
    }

    public static String baseUrl(ConfigurableApplicationContext context) {
        return "http://localhost:" + port(context);
    }

    public static Map<String, Object> authServerProperties() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("server.port", 0);
        props.put("flowguard.auth.introspection-client-id", AUTH_INTROSPECT_CLIENT_ID);
        props.put("flowguard.auth.introspection-client-secret", AUTH_INTROSPECT_CLIENT_SECRET);
        return props;
    }

    public static Map<String, Object> gatewaySecureProperties(
            String backendBaseUrl,
            String authBaseUrl,
            String internalToken) {
        Map<String, Object> props = gatewayCoreProperties(backendBaseUrl, internalToken);
        props.put("spring.cloud.gateway.routes[0].id", "flowguard-auth-server");
        props.put("spring.cloud.gateway.routes[0].uri", authBaseUrl);
        props.put("spring.cloud.gateway.routes[0].predicates[0]", "Path=/oauth2/**,/api/v1/auth/**");
        props.put("spring.cloud.gateway.routes[1].id", "flowguard-backend-api");
        props.put("spring.cloud.gateway.routes[1].uri", backendBaseUrl);
        props.put("spring.cloud.gateway.routes[1].predicates[0]", "Path=/api/v1/**");
        props.put("spring.security.oauth2.resourceserver.opaque-token.introspection-uri",
                authBaseUrl + "/oauth2/introspect");
        props.put("spring.security.oauth2.resourceserver.opaque-token.client-id", AUTH_INTROSPECT_CLIENT_ID);
        props.put("spring.security.oauth2.resourceserver.opaque-token.client-secret", AUTH_INTROSPECT_CLIENT_SECRET);
        props.put("FLOWGUARD_AUTH_SERVER_URL", authBaseUrl);
        // Partner inbox/outbox filter buffers bodies; disable for OAuth proxy E2E (GET health/ops).
        props.put("flowguard.gateway.messaging.enabled", false);
        props.put("GATEWAY_MESSAGING_ENABLED", false);
        return props;
    }

    public static ConfigurableApplicationContext startFixture(boolean prodE2eProfile) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(FixtureWalletApplication.class)
                .profiles("fixture", INTEGRATION_SERVLET_PROFILE);
        if (prodE2eProfile) {
            builder.profiles("prod-e2e-it");
        }
        Map<String, Object> props = fixtureProperties(prodE2eProfile);
        return builder.web(WebApplicationType.SERVLET).properties(props).run(commandLineArgs(props));
    }

    public static ConfigurableApplicationContext startBackend(String fixtureBaseUrl, BackendOptions options) {
        Map<String, Object> props = backendProperties(fixtureBaseUrl, options);
        return new SpringApplicationBuilder(LedgerGuardServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("prod", INTEGRATION_SERVLET_PROFILE)
                .properties(props)
                .run(commandLineArgs(props));
    }

    /** CLI args so {@code @KafkaListener} placeholders and env-style keys bind reliably on Windows E2E. */
    private static String[] commandLineArgs(Map<String, Object> props) {
        List<String> args = new ArrayList<>();
        args.add("--server.port=0");
        props.forEach((key, value) -> appendCommandLineProperty(args, key, value));
        return args.toArray(String[]::new);
    }

    public static void appendCommandLineProperty(List<String> args, String key, Object value) {
        if ("server.port".equals(key) || value == null) {
            return;
        }
        if ("spring.autoconfigure.exclude".equals(key)) {
            String[] parts = value.toString().split(",");
            for (int i = 0; i < parts.length; i++) {
                args.add("--spring.autoconfigure.exclude[" + i + "]=" + parts[i].trim());
            }
            return;
        }
        args.add("--" + key + "=" + value);
    }

    private static final String AUTH_NO_DATASOURCE_AUTOCONFIG =
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";

    public static ConfigurableApplicationContext startAuthServer() {
        Map<String, Object> props = new LinkedHashMap<>(authServerProperties());
        props.put("flowguard.auth.ui-client-id", "flowguard-ui");
        props.put("flowguard.auth.ui-client-secret", "flowguard-ui-secret");
        props.put("flowguard.auth.issuer", "http://127.0.0.1:0");
        isolateServletApp(props);
        props.put(
                "spring.autoconfigure.exclude",
                props.get("spring.autoconfigure.exclude") + "," + AUTH_NO_DATASOURCE_AUTOCONFIG);
        return new SpringApplicationBuilder(AuthServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(INTEGRATION_SERVLET_PROFILE)
                .properties(props)
                .run(commandLineArgs(props));
    }

    public static ConfigurableApplicationContext startGatewayIt(String backendBaseUrl, String internalToken) {
        return startGatewayIt(backendBaseUrl, internalToken, null);
    }

    public static ConfigurableApplicationContext startGatewayIt(
            String backendBaseUrl, String internalToken, Integer fixedPort) {
        Map<String, Object> props = gatewayItProperties(backendBaseUrl, internalToken);
        if (fixedPort != null) {
            props.put("server.port", fixedPort);
        }
        return new SpringApplicationBuilder(ApiGatewayApplication.class)
                .web(WebApplicationType.REACTIVE)
                .profiles("gateway-store", "gateway-it")
                .properties(props)
                .run(commandLineArgs(props));
    }

    public static ConfigurableApplicationContext startGatewaySecure(
            String backendBaseUrl,
            String authBaseUrl,
            String internalToken) {
        Map<String, Object> props = gatewaySecureProperties(backendBaseUrl, authBaseUrl, internalToken);
        return new SpringApplicationBuilder(ApiGatewayApplication.class)
                .web(WebApplicationType.REACTIVE)
                .profiles("gateway-store")
                .properties(props)
                .run(commandLineArgs(props));
    }

    public static Map<String, Object> fixtureProperties(boolean prodE2eProfile) {
        String jdbc = Day1Infrastructure.fixtureJdbcUrl();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("server.port", 0);
        props.put("spring.datasource.url", jdbc);
        props.put("SPRING_DATASOURCE_URL", jdbc);
        props.put("DATASOURCE_URL", jdbc);
        props.put("spring.datasource.username", Day1Infrastructure.POSTGRES.getUsername());
        props.put("spring.datasource.password", Day1Infrastructure.POSTGRES.getPassword());
        props.put("SPRING_DATASOURCE_PASSWORD", Day1Infrastructure.POSTGRES.getPassword());
        props.put("spring.data.redis.host", Day1Infrastructure.REDIS.getHost());
        props.put("spring.data.redis.port", Day1Infrastructure.REDIS.getMappedPort(6379));
        props.put("KAFKA_BOOTSTRAP_SERVERS", Day1Infrastructure.KAFKA.getBootstrapServers());
        props.put("spring.kafka.bootstrap-servers", Day1Infrastructure.KAFKA.getBootstrapServers());
        props.put("spring.kafka.admin.auto-create", false);
        props.put("spring.kafka.admin.fail-fast", false);
        props.put("spring.application.name", "fixture-wallet");
        props.put(
                "flowguard.fixture.variant",
                prodE2eProfile ? "reference-safe" : "dup-credit-vulnerable");
        props.put("spring.flyway.locations", "classpath:db/migration/fixture");
        props.put("spring.flyway.schemas", "fixture");
        props.put("spring.flyway.default-schema", "fixture");
        if (prodE2eProfile) {
            props.put("flowguard.fixture.messaging-enabled", true);
            props.put("flowguard.fixture.redis-idempotency-enabled", true);
            props.put("flowguard.fixture.transactions-topic", "flowguard.transactions");
            props.put("flowguard.fixture.saga-events-topic", "flowguard.saga.events");
            props.put("flowguard.fixture.transaction-outbox-poll-ms", 100);
            props.put("flowguard.fixture.saga-outbox-poll-ms", 100);
        }
        props.put("spring.kafka.producer.properties.spring.json.add.type.headers", false);
        props.put("spring.kafka.producer.properties.enable.idempotence", false);
        props.put("spring.kafka.producer.retries", 10);
        props.put("spring.kafka.properties.request.timeout.ms", 60_000);
        props.put("spring.kafka.properties.delivery.timeout.ms", 120_000);
        isolateFixtureApp(props);
        return props;
    }

    public static Map<String, Object> backendProperties(String fixtureBaseUrl, BackendOptions options) {
        String metadataJdbc = Day1Infrastructure.metadataJdbcUrl();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("server.port", 0);
        props.put("spring.datasource.url", metadataJdbc);
        props.put("SPRING_DATASOURCE_URL", metadataJdbc);
        props.put("spring.datasource.username", Day1Infrastructure.POSTGRES.getUsername());
        props.put("SPRING_DATASOURCE_PASSWORD", Day1Infrastructure.POSTGRES.getPassword());
        props.put("spring.datasource.password", Day1Infrastructure.POSTGRES.getPassword());
        props.put("DATASOURCE_URL", Day1Infrastructure.fixtureJdbcUrl());
        appendKafkaTopicProperties(props);
        props.put("spring.data.redis.host", Day1Infrastructure.REDIS.getHost());
        props.put("spring.data.redis.port", Day1Infrastructure.REDIS.getMappedPort(6379));
        props.put("KAFKA_BOOTSTRAP_SERVERS", Day1Infrastructure.KAFKA.getBootstrapServers());
        props.put("spring.kafka.bootstrap-servers", Day1Infrastructure.KAFKA.getBootstrapServers());
        props.put("spring.kafka.admin.auto-create", false);
        props.put("spring.kafka.admin.fail-fast", false);
        props.put("spring.kafka.consumer.auto-offset-reset", "earliest");
        props.put("spring.kafka.producer.properties.spring.json.add.type.headers", false);
        props.put("spring.kafka.producer.properties.enable.idempotence", false);
        props.put("spring.kafka.producer.retries", 10);
        props.put("spring.kafka.properties.request.timeout.ms", 60_000);
        props.put("spring.kafka.properties.delivery.timeout.ms", 120_000);
        props.put("spring.kafka.listener.concurrency", 1);
        props.put("spring.jpa.properties.hibernate.default_schema", "metadata");
        props.put("FIXTURE_WALLET_URL", fixtureBaseUrl);
        props.put("flowguard.fixture.base-url", fixtureBaseUrl);
        props.put("flowguard.fixture.customer-account-id", "00000000-0000-4000-8000-000000000002");
        props.put("flowguard.fixture.internal-token", "");
        props.put("flowguard.meta.stub-when-no-key", true);
        props.put("flowguard.meta.api-key", "");
        props.put("MODEL_API_KEY", "");
        props.put("flowguard.investigation.sync-pipeline", true);
        props.put("flowguard.production.guards-enabled", false);
        props.put("spring.application.name", "flowguard-backend");
        props.put("spring.flyway.locations", "classpath:db/migration/metadata");
        if (options.apiKey() != null) {
            props.put("FLOWGUARD_API_KEY", options.apiKey());
            props.put("flowguard.security.api-key", options.apiKey());
        } else {
            props.put("FLOWGUARD_API_KEY", "");
            props.put("flowguard.security.api-key", "");
            props.put("FLOWGUARD_OAUTH_ENABLED", "false");
            props.put("flowguard.security.oauth.enabled", false);
        }
        if (options.oauthIntrospectionUri() != null) {
            props.put("flowguard.security.oauth.enabled", true);
            props.put("flowguard.security.oauth.introspection-uri", options.oauthIntrospectionUri());
            props.put("flowguard.security.oauth.client-id", AUTH_INTROSPECT_CLIENT_ID);
            props.put("flowguard.security.oauth.client-secret", AUTH_INTROSPECT_CLIENT_SECRET);
        }
        if (options.gatewayBaseUrl() != null) {
            props.put("flowguard.gateway.base-url", options.gatewayBaseUrl());
            props.put("flowguard.gateway.internal-token", options.gatewayInternalToken());
        } else {
            props.put("flowguard.gateway.base-url", "");
            props.put("flowguard.gateway.internal-token", "");
        }
        props.put("spring.flyway.schemas", "metadata");
        props.put("spring.flyway.default-schema", "metadata");
        isolateServletApp(props);
        return props;
    }

    static Map<String, Object> gatewayItProperties(String backendBaseUrl, String internalToken) {
        Map<String, Object> props = gatewayCoreProperties(backendBaseUrl, internalToken);
        props.put("spring.cloud.gateway.routes[0].id", "flowguard-backend-api");
        props.put("spring.cloud.gateway.routes[0].uri", backendBaseUrl);
        props.put("spring.cloud.gateway.routes[0].predicates[0]", "Path=/api/v1/**");
        return props;
    }

    private static void appendKafkaTopicProperties(Map<String, Object> props) {
        String groupSuffix = Long.toHexString(System.nanoTime());
        String txGroup = "fg-e2e-tx-" + groupSuffix;
        String sagaGroup = "fg-e2e-saga-" + groupSuffix;
        String evidenceGroup = "fg-e2e-evidence-" + groupSuffix;
        props.put("flowguard.kafka.groups.transactions", txGroup);
        props.put("flowguard.kafka.groups.saga-events", sagaGroup);
        props.put("flowguard.kafka.groups.evidence", evidenceGroup);
        props.put("flowguard.kafka.groups.sagaEvents", sagaGroup);
        props.put("FLOWGUARD_MESSAGING_ENABLED", true);
        props.put("flowguard.kafka.messaging-enabled", true);
        props.put("flowguard.kafka.topics.investigations", "flowguard.investigations");
        props.put("flowguard.kafka.topics.experiments", "flowguard.experiments");
        props.put("flowguard.kafka.topics.evidence", "flowguard.evidence");
        props.put("flowguard.kafka.topics.evidence-processed", "flowguard.evidence.processed");
        props.put("flowguard.kafka.topics.transactions", "flowguard.transactions");
        props.put("flowguard.kafka.topics.saga-events", "flowguard.saga.events");
    }

    private static Map<String, Object> gatewayCoreProperties(String backendBaseUrl, String internalToken) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.main.web-application-type", "reactive");
        props.put("spring.cloud.gateway.enabled", true);
        props.put("management.endpoint.health.validate-group-membership", false);
        props.put("server.port", 0);
        String gatewayJdbc = Day1Infrastructure.gatewayJdbcUrl();
        props.put("spring.datasource.url", gatewayJdbc);
        props.put("SPRING_DATASOURCE_URL", gatewayJdbc);
        props.put("spring.datasource.username", Day1Infrastructure.POSTGRES.getUsername());
        props.put("spring.datasource.password", Day1Infrastructure.POSTGRES.getPassword());
        props.put("SPRING_DATASOURCE_PASSWORD", Day1Infrastructure.POSTGRES.getPassword());
        props.put("spring.data.redis.host", Day1Infrastructure.REDIS.getHost());
        props.put("spring.data.redis.port", Day1Infrastructure.REDIS.getMappedPort(6379));
        props.put("spring.kafka.bootstrap-servers", Day1Infrastructure.KAFKA.getBootstrapServers());
        props.put("spring.kafka.admin.auto-create", false);
        props.put("spring.kafka.admin.fail-fast", false);
        props.put("flowguard.gateway.messaging.enabled", true);
        props.put("flowguard.gateway.messaging.backend-base-url", backendBaseUrl);
        props.put("FLOWGUARD_BACKEND_URL", backendBaseUrl);
        props.put("spring.flyway.locations", "classpath:db/migration/gateway");
        props.put("spring.flyway.schemas", "gateway");
        props.put("spring.flyway.default-schema", "gateway");
        if (internalToken != null && !internalToken.isBlank()) {
            props.put("flowguard.gateway.messaging.internal-token", internalToken);
        }
        return props;
    }

    public static EnterpriseStack startEnterpriseStack() {
        ConfigurableApplicationContext fixture = startFixture(true);
        String fixtureUrl = baseUrl(fixture);

        ConfigurableApplicationContext backend = startBackend(fixtureUrl, BackendOptions.opsOnly());
        String backendUrl = baseUrl(backend);
        awaitBackendKafkaListeners(backend);

        ConfigurableApplicationContext gateway = startGatewayIt(backendUrl, GATEWAY_INTERNAL_TOKEN);
        int gatewayPort = port(gateway);
        String gatewayUrl = "http://localhost:" + gatewayPort;

        stopQuietly(backend);
        backend = startBackend(
                fixtureUrl,
                BackendOptions.opsWithGateway(gatewayUrl, GATEWAY_INTERNAL_TOKEN));
        backendUrl = baseUrl(backend);
        awaitBackendKafkaListeners(backend);

        stopQuietly(gateway);
        gateway = startGatewayIt(backendUrl, GATEWAY_INTERNAL_TOKEN, gatewayPort);
        gatewayUrl = baseUrl(gateway);

        return new EnterpriseStack(fixture, backend, gateway, fixtureUrl, backendUrl, gatewayUrl);
    }

    public static void stopQuietly(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }

    public static void awaitBackendKafkaListeners(ConfigurableApplicationContext backendContext) {
        await().atMost(60, TimeUnit.SECONDS).until(() -> {
            KafkaListenerEndpointRegistry registry =
                    backendContext.getBean(KafkaListenerEndpointRegistry.class);
            if (registry.getListenerContainerIds().isEmpty()) {
                return false;
            }
            return registry.getListenerContainers().stream().allMatch(container ->
                    container.isRunning()
                            && container.getAssignedPartitions() != null
                            && !container.getAssignedPartitions().isEmpty());
        });
    }

    public record EnterpriseStack(
            ConfigurableApplicationContext fixture,
            ConfigurableApplicationContext backend,
            ConfigurableApplicationContext gateway,
            String fixtureBaseUrl,
            String backendBaseUrl,
            String gatewayBaseUrl) {
    }

    public record BackendOptions(
            String apiKey,
            String oauthIntrospectionUri,
            String gatewayBaseUrl,
            String gatewayInternalToken) {

        public static BackendOptions open() {
            return new BackendOptions(null, null, null, null);
        }

        public static BackendOptions opsOnly() {
            return new BackendOptions(OPS_API_KEY, null, null, null);
        }

        public static BackendOptions opsWithGateway(String gatewayBaseUrl, String gatewayInternalToken) {
            return new BackendOptions(OPS_API_KEY, null, gatewayBaseUrl, gatewayInternalToken);
        }

        public static BackendOptions oauth(String introspectionUri) {
            return new BackendOptions(null, introspectionUri, null, null);
        }
    }
}
