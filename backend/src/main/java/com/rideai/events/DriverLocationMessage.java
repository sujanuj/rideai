package com.rideai.events;

import java.time.Instant;

/** What goes on the Kafka "driver-locations" topic: a GPS point from a driver who is on a trip. */
public record DriverLocationMessage(
    long driverId,
    long tripId,
    String tripStatus,
    double lat,
    double lng,
    Instant at) {
}
