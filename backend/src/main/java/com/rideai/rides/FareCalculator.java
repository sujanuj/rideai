package com.rideai.rides;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * fare = max(minimum, base + perKm × km + perMinute × minutes), in cents.
 * All numbers are configurable in application.yml (rideai.fare.*).
 */
@Component
public class FareCalculator {

    private final int baseCents;
    private final int perKmCents;
    private final int perMinuteCents;
    private final int minimumCents;

    public FareCalculator(
            @Value("${rideai.fare.base-cents:250}") int baseCents,
            @Value("${rideai.fare.per-km-cents:120}") int perKmCents,
            @Value("${rideai.fare.per-minute-cents:30}") int perMinuteCents,
            @Value("${rideai.fare.minimum-cents:600}") int minimumCents) {
        this.baseCents = baseCents;
        this.perKmCents = perKmCents;
        this.perMinuteCents = perMinuteCents;
        this.minimumCents = minimumCents;
    }

    public int fareCents(int distanceMeters, int durationSeconds) {
        double km = distanceMeters / 1000.0;
        double minutes = durationSeconds / 60.0;
        long fare = Math.round(baseCents + perKmCents * km + perMinuteCents * minutes);
        return (int) Math.max(minimumCents, fare);
    }
}
