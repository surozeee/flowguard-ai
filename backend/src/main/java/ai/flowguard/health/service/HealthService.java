package ai.flowguard.health.service;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private final JdbcTemplate jdbc;
    private final RedisConnectionFactory redisConnectionFactory;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public HealthService(
            JdbcTemplate jdbcTemplate,
            RedisConnectionFactory redisConnectionFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.jdbc = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Map<String, Object> checkDependencies() {
        Map<String, Object> deps = new LinkedHashMap<>();
        deps.put("postgres", pingPostgres());
        deps.put("redis", pingRedis());
        deps.put("kafka", pingKafka());
        deps.put("disk", pingDisk());
        return Map.of("status", "UP", "service", "flowguard-backend", "dependencies", deps);
    }

    private String pingKafka() {
        try {
            kafkaTemplate.getProducerFactory().createProducer().close();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String pingPostgres() {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                jdbc.queryForObject("SELECT 1", Integer.class);
                return "UP";
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return "DOWN";
                }
            }
        }
        return "DOWN";
    }

    private String pingDisk() {
        java.io.File root = new java.io.File(".");
        if (root.getFreeSpace() <= 0) {
            return "DOWN";
        }
        return "UP";
    }

    private String pingRedis() {
        try {
            return redisConnectionFactory.getConnection().ping() != null ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
