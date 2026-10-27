package ai.flowguard.config;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Bean
    ConsumerFactory<String, Map<String, Object>> transactionConsumerFactory(
            KafkaProperties kafkaProperties, FlowGuardProperties flowGuardProperties) {
        return mapConsumerFactory(kafkaProperties, flowGuardProperties.kafka().groups().transactions());
    }

    @Bean
    ConsumerFactory<String, Map<String, Object>> sagaConsumerFactory(
            KafkaProperties kafkaProperties, FlowGuardProperties flowGuardProperties) {
        return mapConsumerFactory(kafkaProperties, flowGuardProperties.kafka().groups().sagaEvents());
    }

    @Bean
    ConsumerFactory<String, Map<String, Object>> evidenceConsumerFactory(
            KafkaProperties kafkaProperties, FlowGuardProperties flowGuardProperties) {
        return mapConsumerFactory(kafkaProperties, flowGuardProperties.kafka().groups().evidence());
    }

    private static ConsumerFactory<String, Map<String, Object>> mapConsumerFactory(
            KafkaProperties kafkaProperties, String groupId) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.keySet().removeIf(key -> key != null && key.toString().startsWith("spring.json."));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<Map<String, Object>> deserializer = new JsonDeserializer<>(MAP_TYPE);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("java.util", "java.lang", "ai.flowguard", "*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> transactionKafkaListenerContainerFactory(
            ConsumerFactory<String, Map<String, Object>> transactionConsumerFactory) {
        return listenerFactory(transactionConsumerFactory);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> sagaKafkaListenerContainerFactory(
            ConsumerFactory<String, Map<String, Object>> sagaConsumerFactory) {
        return listenerFactory(sagaConsumerFactory);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> evidenceKafkaListenerContainerFactory(
            ConsumerFactory<String, Map<String, Object>> evidenceConsumerFactory) {
        return listenerFactory(evidenceConsumerFactory);
    }

    private static ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> listenerFactory(
            ConsumerFactory<String, Map<String, Object>> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler((record, ex) -> log.warn(
                "Kafka listener failed topic={} partition={} offset={}: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                ex.getMessage()), new FixedBackOff(500L, 5L)));
        return factory;
    }
}
