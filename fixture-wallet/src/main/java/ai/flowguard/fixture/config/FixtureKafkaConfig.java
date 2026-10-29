package ai.flowguard.fixture.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "flowguard.fixture.messaging-enabled", matchIfMissing = true)
public class FixtureKafkaConfig {

    @Bean
    NewTopic transactionsTopic(FixtureProperties properties) {
        return TopicBuilder.name(properties.transactionsTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic sagaEventsTopic(FixtureProperties properties) {
        return TopicBuilder.name(properties.sagaEventsTopic()).partitions(3).replicas(1).build();
    }
}
