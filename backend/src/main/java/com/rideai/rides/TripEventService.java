package com.rideai.rides;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.rideai.events.TripEventMessage;

/**
 * The single place trip events are recorded. Each event is:
 *   1. saved to trip_events (the timeline the AI assistant reads), and
 *   2. announced inside the app; after the transaction commits, listeners push it to
 *      Kafka (KafkaEventPublisher) and to WebSocket clients (RealtimeNotifier).
 */
@Service
public class TripEventService {

    private final TripEventRepository events;
    private final ApplicationEventPublisher publisher;

    public TripEventService(TripEventRepository events, ApplicationEventPublisher publisher) {
        this.events = events;
        this.publisher = publisher;
    }

    public void record(Trip trip, String type) {
        record(trip, type, Map.of());
    }

    public void record(Trip trip, String type, Map<String, Object> payload) {
        events.save(new TripEvent(trip.getId(), type, payload));
        publisher.publishEvent(new TripEventMessage(
            UUID.randomUUID().toString(),
            trip.getId(),
            type,
            trip.getStatus().name(),
            trip.getRiderId(),
            trip.getDriverId(),
            Instant.now(),
            payload));
    }
}
