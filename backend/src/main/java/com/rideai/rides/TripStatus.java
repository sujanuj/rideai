package com.rideai.rides;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A trip is a state machine. Only these moves are legal:
 *
 *   REQUESTED ──accept──▶ ACCEPTED ──arrive──▶ ARRIVED ──start──▶ IN_PROGRESS ──complete──▶ COMPLETED
 *       │                    │                    │
 *       └──────cancel────────┴───────cancel───────┴──▶ CANCELLED
 *
 * Keeping the rules in one place (instead of scattered if-statements and boolean flags)
 * makes illegal states impossible and easy to test.
 */
public enum TripStatus {
    REQUESTED,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<TripStatus, Set<TripStatus>> ALLOWED = Map.of(
        REQUESTED, EnumSet.of(ACCEPTED, CANCELLED),
        ACCEPTED, EnumSet.of(ARRIVED, CANCELLED),
        ARRIVED, EnumSet.of(IN_PROGRESS, CANCELLED),
        IN_PROGRESS, EnumSet.of(COMPLETED),
        COMPLETED, EnumSet.noneOf(TripStatus.class),
        CANCELLED, EnumSet.noneOf(TripStatus.class));

    /** Trips that still tie up a rider (and, once accepted, a driver). */
    public static final Set<TripStatus> ACTIVE = EnumSet.of(REQUESTED, ACCEPTED, ARRIVED, IN_PROGRESS);

    public boolean canMoveTo(TripStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED;
    }
}
