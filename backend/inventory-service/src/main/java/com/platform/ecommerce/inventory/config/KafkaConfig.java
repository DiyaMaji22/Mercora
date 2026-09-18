package com.platform.ecommerce.inventory.config;

import com.platform.ecommerce.inventory.event.InventoryUpdateEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.config.TopicBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Producer configuration for the `inventory-events` topic (3 partitions,
 * keyed by productId so all events for a product are ordered). The
 * corresponding DLQ topic (`inventory-events.DLQ`) receives events that
 * fail all retry attempts on the consumer side (see Order/Product services'
 * consumer configs, which mirror this pattern with a DefaultErrorHandler).
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name("inventory-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryEventsDlqTopic() {
        return TopicBuilder.name("inventory-events.DLQ")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, InventoryUpdateEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // idempotent producer: safe retries without duplicate inventory events
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 5);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, InventoryUpdateEvent> kafkaTemplate(
            ProducerFactory<String, InventoryUpdateEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
