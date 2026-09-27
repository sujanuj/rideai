package com.rideai.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.rideai.common.GeoPoint;

class FareAndRoutingTest {

    private final FareCalculator fares = new FareCalculator(250, 120, 30, 600);

    @Test
    void fareAddsBaseDistanceAndTime() {
        // 250 + 120 * 10 km + 30 * 20 min = 250 + 1200 + 600
        assertThat(fares.fareCents(10_000, 1_200)).isEqualTo(2_050);
    }

    @Test
    void shortTripsPayTheMinimum() {
        assertThat(fares.fareCents(800, 120)).isEqualTo(600);
    }

    @Test
    void haversineDistanceIsAccurate() {
        // ASU Tempe campus to Phoenix Sky Harbor airport: about 7.7 km in a straight line
        GeoPoint asu = new GeoPoint(33.4242, -111.9281);
        GeoPoint phx = new GeoPoint(33.4352, -112.0101);
        assertThat(asu.distanceMetersTo(phx)).isCloseTo(7_700, within(600.0));
    }

    @Test
    void straightLineFallbackAddsDetourAndSpeed() {
        GeoPoint a = new GeoPoint(33.4242, -111.9281);
        GeoPoint b = new GeoPoint(33.4352, -112.0101);
        RoutingService.Route route = RoutingService.straightLine(a, b);

        assertThat(route.source()).isEqualTo("estimate");
        assertThat(route.distanceMeters()).isGreaterThan((int) a.distanceMetersTo(b));
        assertThat(route.durationSeconds()).isPositive();
        assertThat(route.path()).containsExactly(a, b);
    }
}
