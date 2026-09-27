package com.rideai.drivers;

public enum DriverStatus {
    /** Not taking rides. */
    OFFLINE,
    /** Online and free: appears in nearby-driver searches. */
    AVAILABLE,
    /** Assigned to a trip. */
    ON_TRIP
}
