package com.rideai.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Publishes trip events to Kafka only AFTER the database transaction commits, so consumers
 * never see an event for a change that was rolled back. (The fully robust version of this is
 * the "transactional outbox" pattern; this is the simple version.)
 */
@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripEvent(TripEventMessage event) {
        send(Topics.TRIP_EVENTS, String.valueOf(event.tripId()), event);
    }

    public void driverLocation(DriverLocationMessage location) {
        send(Topics.DRIVER_LOCATIONS, String.valueOf(location.driverId()), location);
    }

    private void send(String topic, String key, Object value) {
        try {
            kafka.send(topic, key, json.writeValueAsString(value))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Kafka send to {} failed: {}", topic, ex.getMessage());
                    }
                });
        } catch (JsonProcessingException | RuntimeException e) {
            // Never fail a user request because Kafka is down; the matching scheduler is a safety net.
            log.warn("Could not publish to {}: {}", topic, e.getMessage());
        }
    }
}
