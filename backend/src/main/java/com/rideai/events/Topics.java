package com.rideai.events;

/** Kafka topic names and consumer groups, in one place so producers and consumers never drift apart. */
public final class Topics {

    /** Every trip state change, keyed by tripId so one trip's events stay in order. */
    public static final String TRIP_EVENTS = "trip-events";

    /** GPS updates from drivers on a trip, keyed by driverId. */
    public static final String DRIVER_LOCATIONS = "driver-locations";

    /** Suffix for dead-letter topics: messages that kept failing end up here for inspection. */
    public static final String DLT_SUFFIX = ".DLT";

    public static final String GROUP_MATCHING = "rideai-matching";
    public static final String GROUP_TRIP_MONITOR = "rideai-trip-monitor";
    public static final String GROUP_AI = "rideai-ai";

    private Topics() {
    }
}
