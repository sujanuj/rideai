package com.rideai.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Spring's KafkaAdmin creates these topics on startup if they don't exist yet.
 * 3 partitions lets up to 3 consumers in one group share the work.
 * Replication is 1 because we run a single local broker.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic tripEventsTopic() {
        return TopicBuilder.name(Topics.TRIP_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic driverLocationsTopic() {
        return TopicBuilder.name(Topics.DRIVER_LOCATIONS).partitions(3).replicas(1).build();
    }
}
