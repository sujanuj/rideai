package com.rideai.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.rideai.common.ApiException;
import com.rideai.common.GeoPoint;

class TripStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @CsvSource({
        "REQUESTED, ACCEPTED",
        "REQUESTED, CANCELLED",
        "ACCEPTED, ARRIVED",
        "ACCEPTED, CANCELLED",
        "ARRIVED, IN_PROGRESS",
        "ARRIVED, CANCELLED",
        "IN_PROGRESS, COMPLETED"
    })
    void legalMoves(TripStatus from, TripStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} rejected")
    @CsvSource({
        "REQUESTED, IN_PROGRESS",
        "REQUESTED, COMPLETED",
        "ACCEPTED, COMPLETED",
        "ACCEPTED, REQUESTED",
        "IN_PROGRESS, CANCELLED",
        "COMPLETED, ACCEPTED",
        "COMPLETED, CANCELLED",
        "CANCELLED, ACCEPTED"
    })
    void illegalMoves(TripStatus from, TripStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @Test
    void finishedStatesHaveNoWayOut() {
        for (TripStatus next : TripStatus.values()) {
            assertThat(TripStatus.COMPLETED.canMoveTo(next)).isFalse();
            assertThat(TripStatus.CANCELLED.canMoveTo(next)).isFalse();
        }
    }

    @Test
    void tripRecordsTimestampsAndRejectsIllegalMoves() {
        Trip trip = new Trip(1L, new GeoPoint(33.42, -111.94), new GeoPoint(33.45, -111.98), 5000, 600, 1200);
        trip.offerTo(7L, java.time.Instant.now().plusSeconds(15));

        trip.moveTo(TripStatus.ACCEPTED);
        assertThat(trip.getAcceptedAt()).isNotNull();
        assertThat(trip.getOfferedDriverId()).isNull(); // offer cleared once accepted

        assertThatThrownBy(() -> trip.moveTo(TripStatus.COMPLETED))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("ACCEPTED to COMPLETED");
    }
}
