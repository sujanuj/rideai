package com.rideai.rides;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.rideai.common.GeoPoint;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TripDtos {

    private TripDtos() {
    }

    public record TripRequest(@Valid @NotNull GeoPoint pickup, @Valid @NotNull GeoPoint dropoff) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    public record EstimateResponse(
        int distanceMeters,
        int durationSeconds,
        int fareCents,
        List<GeoPoint> route,
        String routeSource) {
    }

    public record DriverInfo(
        long id,
        String name,
        String vehicle,
        String plate,
        BigDecimal rating,
        GeoPoint position) {
    }

    public record TripResponse(
        long id,
        TripStatus status,
        GeoPoint pickup,
        GeoPoint dropoff,
        Integer distanceMeters,
        Integer durationSeconds,
        Integer fareCents,
        Integer pickupEtaSeconds,
        Integer actualDistanceMeters,
        long riderId,
        String riderName,
        DriverInfo driver,
        Instant offerExpiresAt,
        Instant requestedAt,
        Instant acceptedAt,
        Instant arrivedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason) {
    }

    public record TimelineEntry(String type, Object payload, Instant at) {
    }
}
