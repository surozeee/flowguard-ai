package ai.flowguard.fixture.health.service;

import ai.flowguard.fixture.config.FixtureProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FixtureHealthService {

    private final FixtureProperties properties;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplate;

    public FixtureHealthService(
            FixtureProperties properties,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplate) {
        this.properties = properties;
        this.jdbc = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Map<String, Object> check() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("variant", properties.variant());
        body.put("messagingEnabled", properties.messagingEnabled());
        body.put("redisIdempotencyEnabled", properties.redisIdempotencyEnabled());

        Map<String, String> deps = new LinkedHashMap<>();
        deps.put("postgres", pingPostgres());
        deps.put("redis", pingRedis());
        deps.put("kafka", pingKafka());
        body.put("dependencies", deps);
        return body;
    }

    private String pingPostgres() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String pingRedis() {
        if (!properties.redisIdempotencyEnabled()) {
            return "DISABLED";
        }
        RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return "NOT_CONFIGURED";
        }
        try {
            return factory.getConnection().ping() != null ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String pingKafka() {
        if (!properties.messagingEnabled()) {
            return "DISABLED";
        }
        KafkaTemplate<String, Object> template = kafkaTemplate.getIfAvailable();
        if (template == null) {
            return "NOT_CONFIGURED";
        }
        Object servers = template.getProducerFactory().getConfigurationProperties().get("bootstrap.servers");
        return servers == null ? "DOWN" : "UP";
    }
}
