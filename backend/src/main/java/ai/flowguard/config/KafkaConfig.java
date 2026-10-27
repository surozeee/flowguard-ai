package ai.flowguard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic investigationsTopic(FlowGuardProperties properties) {
        return TopicBuilder.name(properties.kafka().topics().investigations()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic experimentsTopic(FlowGuardProperties properties) {
        return TopicBuilder.name(properties.kafka().topics().experiments()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic evidenceTopic(FlowGuardProperties properties) {
        return TopicBuilder.name(properties.kafka().topics().evidence()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic evidenceProcessedTopic(FlowGuardProperties properties) {
        return TopicBuilder.name(properties.kafka().topics().evidenceProcessed()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic transactionsTopic(FlowGuardProperties properties) {
        return TopicBuilder.name(properties.kafka().topics().transactions()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic sagaEventsTopic(FlowGuardProperties properties) {
        return TopicBuilder.name(properties.kafka().topics().sagaEvents()).partitions(3).replicas(1).build();
    }
}
