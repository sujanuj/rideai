package com.rideai.events;

/** Kafka topic names, in one place so producers and consumers never drift apart. */
public final class Topics {

    /** Every trip state change, keyed by tripId so one trip's events stay in order. */
    public static final String TRIP_EVENTS = "trip-events";

    /** GPS updates from drivers, keyed by driverId. */
    public static final String DRIVER_LOCATIONS = "driver-locations";

    private Topics() {
    }
}
