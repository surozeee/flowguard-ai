package ai.flowguard.day1;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Shared Testcontainers network for Day 1 stack tests.
 */
public final class Day1Infrastructure {

    public static final Network NETWORK = Network.newNetwork();

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("flowguard")
            .withUsername("flowguard")
            .withPassword("flowguard")
            .withInitScript("day1/postgres-init.sql");

    public static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("redis")
            .withExposedPorts(6379);

    public static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka");

    private static int useCount;

    private static final List<String> E2E_KAFKA_TOPICS = List.of(
            "flowguard.transactions",
            "flowguard.saga.events",
            "flowguard.investigations",
            "flowguard.experiments",
            "flowguard.evidence",
            "flowguard.evidence.processed");

    private Day1Infrastructure() {
    }

    public static synchronized void startAll() {
        if (useCount++ == 0) {
            POSTGRES.start();
            REDIS.start();
            KAFKA.start();
            ensureKafkaTopics();
        }
    }

    private static void ensureKafkaTopics() {
        Map<String, Object> config =
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(config)) {
            admin.describeCluster().nodes().get(60, TimeUnit.SECONDS);
            List<NewTopic> topics = E2E_KAFKA_TOPICS.stream()
                    .map(name -> new NewTopic(name, 3, (short) 1))
                    .toList();
            try {
                admin.createTopics(topics).all().get(60, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                if (!(ex.getCause() instanceof TopicExistsException)) {
                    throw new IllegalStateException("Failed to create Kafka topics for E2E", ex);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka not ready for E2E", ex);
        }
    }

    public static synchronized void stopAll() {
        if (useCount > 0 && --useCount == 0) {
            KAFKA.stop();
            REDIS.stop();
            POSTGRES.stop();
        }
    }

    public static String fixtureJdbcUrl() {
        return jdbcUrlWithSchema("fixture");
    }

    public static String metadataJdbcUrl() {
        return jdbcUrlWithSchema("metadata");
    }

    public static String gatewayJdbcUrl() {
        return jdbcUrlWithSchema("gateway");
    }

    private static String jdbcUrlWithSchema(String schema) {
        String base = POSTGRES.getJdbcUrl();
        int query = base.indexOf('?');
        if (query >= 0) {
            base = base.substring(0, query);
        }
        return base + "?currentSchema=" + schema;
    }

    /** Command-line args beat OS env (e.g. SPRING_DATASOURCE_* from a local .env). */
    public static String[] commandLineArgs(Map<String, Object> props) {
        List<String> args = new ArrayList<>();
        args.add("--server.port=0");
        props.forEach((key, value) -> ai.flowguard.e2e.E2EStackSupport.appendCommandLineProperty(args, key, value));
        return args.toArray(String[]::new);
    }
}
