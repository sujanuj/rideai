package com.rideai.events;

import java.time.Instant;
import java.util.Map;

/**
 * What goes on the Kafka "trip-events" topic (as JSON), keyed by tripId so every
 * event for one trip lands on the same partition and is consumed in order.
 *
 * @param eventId unique id, lets consumers skip duplicates (Kafka delivers at least once)
 * @param type    RIDE_REQUESTED, DRIVER_OFFERED, DRIVER_DECLINED, DRIVER_ASSIGNED, DRIVER_ARRIVED,
 *                TRIP_STARTED, TRIP_COMPLETED, TRIP_CANCELLED, NO_DRIVERS_AVAILABLE, DELAY_DETECTED
 */
public record TripEventMessage(
    String eventId,
    long tripId,
    String type,
    String status,
    long riderId,
    Long driverId,
    Instant occurredAt,
    Map<String, Object> payload) {
}
