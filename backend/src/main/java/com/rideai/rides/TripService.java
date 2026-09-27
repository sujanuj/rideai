package com.rideai.rides;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rideai.auth.User;
import com.rideai.auth.UserRepository;
import com.rideai.common.ApiException;
import com.rideai.common.GeoPoint;
import com.rideai.drivers.Driver;
import com.rideai.drivers.DriverLocationService;
import com.rideai.drivers.DriverRepository;
import com.rideai.drivers.DriverService;
import com.rideai.matching.MatchingService;
import com.rideai.rides.RoutingService.Route;
import com.rideai.rides.TripDtos.DriverInfo;
import com.rideai.rides.TripDtos.EstimateResponse;
import com.rideai.rides.TripDtos.TimelineEntry;
import com.rideai.rides.TripDtos.TripRequest;
import com.rideai.rides.TripDtos.TripResponse;

@Service
public class TripService {

    private static final int MIN_TRIP_METERS = 200;
    private static final int MAX_TRIP_METERS = 150_000;

    private final TripRepository trips;
    private final TripEventRepository events;
    private final UserRepository users;
    private final DriverRepository drivers;
    private final DriverService driverService;
    private final DriverLocationService locations;
    private final RoutingService routing;
    private final FareCalculator fares;
    private final MatchingService matching;

    public TripService(TripRepository trips, TripEventRepository events, UserRepository users,
                       DriverRepository drivers, DriverService driverService, DriverLocationService locations,
                       RoutingService routing, FareCalculator fares, MatchingService matching) {
        this.trips = trips;
        this.events = events;
        this.users = users;
        this.drivers = drivers;
        this.driverService = driverService;
        this.locations = locations;
        this.routing = routing;
        this.fares = fares;
        this.matching = matching;
    }

    // ---------- rider ----------

    public EstimateResponse estimate(TripRequest req) {
        Route route = routeFor(req.pickup(), req.dropoff());
        return new EstimateResponse(route.distanceMeters(), route.durationSeconds(),
            fares.fareCents(route.distanceMeters(), route.durationSeconds()), route.path(), route.source());
    }

    @Transactional
    public TripResponse request(long riderId, TripRequest req) {
        trips.findFirstByRiderIdAndStatusIn(riderId, TripStatus.ACTIVE).ifPresent(t -> {
            throw ApiException.conflict("TRIP_IN_PROGRESS", "You already have an active trip (#" + t.getId() + ")");
        });
        Route route = routeFor(req.pickup(), req.dropoff());
        int fare = fares.fareCents(route.distanceMeters(), route.durationSeconds());

        Trip trip = trips.save(new Trip(riderId, req.pickup(), req.dropoff(),
            route.distanceMeters(), route.durationSeconds(), fare));
        events.record(trip.getId(), "RIDE_REQUESTED", Map.of(
            "distanceMeters", route.distanceMeters(),
            "durationSeconds", route.durationSeconds(),
            "fareCents", fare,
            "routeSource", route.source()));

        matching.offerNext(trip.getId());
        return toResponse(trip);
    }

    // ---------- driver ----------

    @Transactional(readOnly = true)
    public Optional<TripResponse> currentOffer(long driverId) {
        return trips.findFirstByOfferedDriverIdAndStatusAndOfferExpiresAtAfter(driverId, TripStatus.REQUESTED, Instant.now())
            .map(this::toResponse);
    }

    @Transactional
    public TripResponse accept(long tripId, long driverId) {
        int updated = trips.acceptIfStillOffered(tripId, driverId, Instant.now());
        if (updated == 0) {
            throw ApiException.conflict("OFFER_UNAVAILABLE", "This ride was taken, cancelled, or the offer expired");
        }
        driverService.startTrip(driverId);
        events.record(tripId, "DRIVER_ASSIGNED", Map.of("driverId", driverId));
        return toResponse(load(tripId));
    }

    @Transactional
    public void decline(long tripId, long driverId) {
        Trip trip = load(tripId);
        if (trip.getStatus() != TripStatus.REQUESTED || !Long.valueOf(driverId).equals(trip.getOfferedDriverId())) {
            throw ApiException.conflict("NO_OFFER", "There is no open offer for you on this trip");
        }
        matching.decline(trip, driverId);
    }

    @Transactional
    public TripResponse arrive(long tripId, long driverId) {
        return driverMoves(tripId, driverId, TripStatus.ARRIVED, "DRIVER_ARRIVED");
    }

    @Transactional
    public TripResponse start(long tripId, long driverId) {
        return driverMoves(tripId, driverId, TripStatus.IN_PROGRESS, "TRIP_STARTED");
    }

    @Transactional
    public TripResponse complete(long tripId, long driverId) {
        TripResponse response = driverMoves(tripId, driverId, TripStatus.COMPLETED, "TRIP_COMPLETED");
        driverService.finishTrip(driverId);
        return response;
    }

    // ---------- both ----------

    @Transactional
    public TripResponse cancel(long tripId, long userId, String reason) {
        Trip trip = load(tripId);
        boolean isRider = trip.getRiderId() == userId;
        boolean isDriver = Long.valueOf(userId).equals(trip.getDriverId());
        if (!isRider && !isDriver) {
            throw ApiException.forbidden("Only the rider or the assigned driver can cancel this trip");
        }
        trip.moveTo(TripStatus.CANCELLED);
        String who = isRider ? "RIDER" : "DRIVER";
        trip.setCancelReason(reason == null || reason.isBlank() ? "CANCELLED_BY_" + who : reason.trim());
        events.record(tripId, "TRIP_CANCELLED", Map.of("by", who, "reason", trip.getCancelReason()));
        if (trip.getDriverId() != null) {
            driverService.finishTrip(trip.getDriverId());
        }
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public TripResponse get(long tripId, long userId) {
        Trip trip = load(tripId);
        checkCanView(trip, userId);
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(long tripId, long userId) {
        checkCanView(load(tripId), userId);
        return events.findByTripIdOrderByCreatedAtAscIdAsc(tripId).stream()
            .map(e -> new TimelineEntry(e.getType(), e.getPayload(), e.getCreatedAt()))
            .toList();
    }

    /** The trip a rider or driver is currently in, if any (lets the app resume after a reload). */
    @Transactional(readOnly = true)
    public Optional<TripResponse> current(long userId) {
        return trips.findFirstByRiderIdAndStatusIn(userId, TripStatus.ACTIVE)
            .or(() -> trips.findFirstByDriverIdAndStatusIn(userId, TripStatus.ACTIVE))
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> history(long userId) {
        return trips.findHistory(userId, PageRequest.of(0, 20)).stream().map(this::toResponse).toList();
    }

    // ---------- helpers ----------

    private TripResponse driverMoves(long tripId, long driverId, TripStatus next, String eventType) {
        Trip trip = load(tripId);
        if (!Long.valueOf(driverId).equals(trip.getDriverId())) {
            throw ApiException.forbidden("You are not the driver on this trip");
        }
        trip.moveTo(next);
        events.record(tripId, eventType);
        return toResponse(trip);
    }

    private Route routeFor(GeoPoint pickup, GeoPoint dropoff) {
        double straight = pickup.distanceMetersTo(dropoff);
        if (straight < MIN_TRIP_METERS) {
            throw ApiException.badRequest("TOO_SHORT", "Pickup and drop-off are too close together");
        }
        if (straight > MAX_TRIP_METERS) {
            throw ApiException.badRequest("TOO_LONG", "Trips are limited to 150 km");
        }
        return routing.route(pickup, dropoff);
    }

    private void checkCanView(Trip trip, long userId) {
        boolean allowed = trip.getRiderId() == userId
            || Long.valueOf(userId).equals(trip.getDriverId())
            || Long.valueOf(userId).equals(trip.getOfferedDriverId());
        if (!allowed) {
            throw ApiException.notFound("Trip"); // don't reveal that the trip exists
        }
    }

    private Trip load(long tripId) {
        return trips.findById(tripId).orElseThrow(() -> ApiException.notFound("Trip"));
    }

    private TripResponse toResponse(Trip t) {
        String riderName = users.findById(t.getRiderId()).map(User::getFullName).orElse("Rider");
        DriverInfo driver = t.getDriverId() == null ? null : driverInfo(t.getDriverId(), t.getStatus());
        return new TripResponse(
            t.getId(), t.getStatus(), t.pickup(), t.dropoff(),
            t.getEstDistanceM(), t.getEstDurationS(), t.getFareCents(),
            t.getRiderId(), riderName, driver, t.getOfferExpiresAt(),
            t.getRequestedAt(), t.getAcceptedAt(), t.getArrivedAt(), t.getStartedAt(),
            t.getCompletedAt(), t.getCancelledAt(), t.getCancelReason());
    }

    private DriverInfo driverInfo(long driverId, TripStatus status) {
        String name = users.findById(driverId).map(User::getFullName).orElse("Driver");
        Driver d = drivers.findById(driverId).orElse(null);
        GeoPoint position = status.isFinished() ? null : locations.position(driverId).orElse(null);
        return new DriverInfo(driverId, name,
            d == null ? null : d.getVehicle(),
            d == null ? null : d.getPlate(),
            d == null ? null : d.getRating(),
            position);
    }
}
