package com.rideai.ai;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.rideai.rides.Trip;
import com.rideai.rides.TripEvent;
import com.rideai.rides.TripEventRepository;
import com.rideai.rides.TripRepository;
import com.rideai.rides.TripStatus;

/** Turns a completed trip's timeline into TripMetrics and deterministic flags. */
@Component
public class TripMetricsCalculator {

    // Flag thresholds
    static final double SLOW_MATCH_MINUTES = 1.5;
    static final double LATE_PICKUP_MINUTES = 3;
    static final double LONG_WAIT_MINUTES = 8;
    static final double LONG_BOARDING_MINUTES = 5;
    static final double SLOW_RIDE_PERCENT = 25;
    static final double DETOUR_PERCENT = 20;

    private final TripRepository trips;
    private final TripEventRepository events;
    private final ZoneId zone;

    public TripMetricsCalculator(TripRepository trips, TripEventRepository events,
                                 @Value("${rideai.ai.time-zone:America/Phoenix}") String zone) {
        this.trips = trips;
        this.events = events;
        this.zone = ZoneId.of(zone);
    }

    public TripMetrics forTrip(Trip trip) {
        List<TripEvent> timeline = events.findByTripIdOrderByCreatedAtAscIdAsc(trip.getId());
        List<Trip> past = trips.findByRiderIdAndStatusAndIdNotOrderByRequestedAtDesc(
            trip.getRiderId(), TripStatus.COMPLETED, trip.getId(), PageRequest.of(0, 10));
        return compute(trip, timeline, past, zone);
    }

    /** Pure function of its inputs, so it's easy to unit test. */
    static TripMetrics compute(Trip trip, List<TripEvent> timeline, List<Trip> pastTrips, ZoneId zone) {
        ZonedDateTime requested = trip.getRequestedAt().atZone(zone);

        Double toFindDriver = minutes(trip.getRequestedAt(), trip.getAcceptedAt());
        int offers = count(timeline, "DRIVER_OFFERED");
        int declined = count(timeline, "DRIVER_DECLINED");
        int delayAlerts = count(timeline, "DELAY_DETECTED");

        Double pickupEta = trip.getPickupEtaS() == null ? null : round(trip.getPickupEtaS() / 60.0);
        Double pickupWait = minutes(trip.getAcceptedAt(), trip.getArrivedAt());
        Double pickupDelay = pickupWait == null || pickupEta == null ? null : round(pickupWait - pickupEta);
        Double boarding = minutes(trip.getArrivedAt(), trip.getStartedAt());

        Double estRide = trip.getEstDurationS() == null ? null : round(trip.getEstDurationS() / 60.0);
        Double actualRide = minutes(trip.getStartedAt(), trip.getCompletedAt());
        Double rideDelayPct = percentOver(actualRide, estRide);

        Double estKm = trip.getEstDistanceM() == null ? null : round(trip.getEstDistanceM() / 1000.0);
        Double actualKm = trip.getActualDistanceM() == null ? null : round(trip.getActualDistanceM() / 1000.0);
        Double detourPct = percentOver(actualKm, estKm);

        Double fare = trip.getFareCents() == null ? null : round(trip.getFareCents() / 100.0);
        Double farePerKm = fare == null || estKm == null || estKm == 0 ? null : round(fare / estKm);

        Double avgPastWait = pastTrips.stream()
            .map(t -> minutes(t.getAcceptedAt(), t.getArrivedAt()))
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average()
            .stream().boxed().findFirst().map(TripMetricsCalculator::round).orElse(null);

        List<String> flags = new ArrayList<>();
        if ((toFindDriver != null && toFindDriver > SLOW_MATCH_MINUTES) || declined >= 2) flags.add("SLOW_MATCH");
        if (pickupDelay != null && pickupDelay > LATE_PICKUP_MINUTES) flags.add("LATE_PICKUP");
        if (pickupWait != null && pickupWait > LONG_WAIT_MINUTES) flags.add("LONG_WAIT");
        if (boarding != null && boarding > LONG_BOARDING_MINUTES) flags.add("LONG_BOARDING");
        if (rideDelayPct != null && rideDelayPct > SLOW_RIDE_PERCENT) flags.add("SLOW_RIDE");
        if (detourPct != null && detourPct > DETOUR_PERCENT) flags.add("ROUTE_DEVIATION");
        if (delayAlerts > 0) flags.add("DELAY_ALERT");
        if (flags.isEmpty()) flags.add("SMOOTH_TRIP");

        return new TripMetrics(
            trip.getId(),
            requested.format(DateTimeFormatter.ofPattern("EEEE h:mm a", Locale.US)),
            requested.getHour(),
            requested.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US),
            toFindDriver, offers, declined,
            pickupEta, pickupWait, pickupDelay, boarding,
            estRide, actualRide, rideDelayPct,
            estKm, actualKm, detourPct,
            fare, farePerKm,
            delayAlerts,
            minutes(trip.getRequestedAt(), trip.getCompletedAt()),
            pastTrips.size(), avgPastWait,
            List.copyOf(flags));
    }

    private static int count(List<TripEvent> timeline, String type) {
        return (int) timeline.stream().filter(e -> type.equals(e.getType())).count();
    }

    private static Double minutes(Instant from, Instant to) {
        return from == null || to == null ? null : round(Duration.between(from, to).toMillis() / 60_000.0);
    }

    private static Double percentOver(Double actual, Double estimate) {
        return actual == null || estimate == null || estimate == 0 ? null : round((actual - estimate) / estimate * 100);
    }

    static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
