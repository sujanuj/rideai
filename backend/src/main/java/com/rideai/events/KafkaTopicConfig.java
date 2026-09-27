package com.rideai.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Topics are created on startup if missing. 3 partitions lets up to 3 consumers in one
 * group share the work; replication is 1 because we run a single local broker.
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

    @Bean
    NewTopic tripEventsDeadLetterTopic() {
        return TopicBuilder.name(Topics.TRIP_EVENTS + Topics.DLT_SUFFIX).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic driverLocationsDeadLetterTopic() {
        return TopicBuilder.name(Topics.DRIVER_LOCATIONS + Topics.DLT_SUFFIX).partitions(1).replicas(1).build();
    }

    /**
     * If a listener throws, retry twice (1 s apart), then park the message on "<topic>.DLT"
     * instead of blocking the partition forever. Spring Boot applies this to every @KafkaListener.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition(record.topic() + Topics.DLT_SUFFIX, 0));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }
}
