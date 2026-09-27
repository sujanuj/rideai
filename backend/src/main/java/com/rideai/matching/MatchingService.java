package com.rideai.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rideai.drivers.Driver;
import com.rideai.drivers.DriverLocationService;
import com.rideai.drivers.DriverLocationService.NearbyDriver;
import com.rideai.drivers.DriverRepository;
import com.rideai.drivers.DriverStatus;
import com.rideai.rides.Trip;
import com.rideai.rides.TripEventService;
import com.rideai.rides.TripRepository;
import com.rideai.rides.TripStatus;

/**
 * Finds a driver for a REQUESTED trip, one driver at a time:
 *   1. GEOSEARCH Redis for available drivers near the pickup, nearest first.
 *   2. Skip drivers we already tried, drivers holding another offer, and anyone not AVAILABLE.
 *   3. Offer the trip to the nearest remaining driver for N seconds.
 *   4. On decline or expiry, try the next driver (triggered by Kafka, with MatchingScheduler as a safety net).
 *   5. If nobody takes it within the search timeout, cancel with NO_DRIVERS_AVAILABLE.
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);
    private static final Duration TRIED_TTL = Duration.ofHours(1);

    private final TripRepository trips;
    private final TripEventService events;
    private final DriverRepository drivers;
    private final DriverLocationService locations;
    private final StringRedisTemplate redis;
    private final double radiusKm;
    private final Duration offerTimeout;
    private final Duration searchTimeout;

    public MatchingService(TripRepository trips, TripEventService events, DriverRepository drivers,
                           DriverLocationService locations, StringRedisTemplate redis,
                           @Value("${rideai.matching.radius-km:5}") double radiusKm,
                           @Value("${rideai.matching.offer-timeout:15s}") Duration offerTimeout,
                           @Value("${rideai.matching.search-timeout:90s}") Duration searchTimeout) {
        this.trips = trips;
        this.events = events;
        this.drivers = drivers;
        this.locations = locations;
        this.redis = redis;
        this.radiusKm = radiusKm;
        this.offerTimeout = offerTimeout;
        this.searchTimeout = searchTimeout;
    }

    /** Offer the trip to the next-best driver, or give up if we've searched long enough. Safe to call twice. */
    @Transactional
    public void offerNext(long tripId) {
        Trip trip = trips.findByIdForUpdate(tripId).orElse(null);
        Instant now = Instant.now();
        if (trip == null || trip.getStatus() != TripStatus.REQUESTED || trip.hasLiveOffer(now)) {
            return;
        }

        Long next = pickDriver(trip, now);
        if (next != null) {
            trip.offerTo(next, now.plus(offerTimeout));
            redis.opsForSet().add(triedKey(tripId), String.valueOf(next));
            redis.expire(triedKey(tripId), TRIED_TTL);
            events.record(trip, "DRIVER_OFFERED", Map.of("driverId", next));
            log.info("Trip {} offered to driver {}", tripId, next);
            return;
        }

        if (trip.getOfferedDriverId() != null) {
            trip.clearOffer(); // the previous offer expired
        }
        if (trip.getRequestedAt().plus(searchTimeout).isBefore(now)) {
            trip.moveTo(TripStatus.CANCELLED);
            trip.setCancelReason("NO_DRIVERS_AVAILABLE");
            events.record(trip, "NO_DRIVERS_AVAILABLE");
            log.info("Trip {} cancelled: no drivers accepted within {}", tripId, searchTimeout);
        }
        // Otherwise stay REQUESTED; the scheduler retries in a couple of seconds.
    }

    /** Driver said no: free the offer. The DRIVER_DECLINED event on Kafka triggers the next offer. */
    @Transactional
    public void decline(Trip trip, long driverId) {
        trip.clearOffer();
        events.record(trip, "DRIVER_DECLINED", Map.of("driverId", driverId));
    }

    private Long pickDriver(Trip trip, Instant now) {
        List<NearbyDriver> nearby = locations.findNearby(trip.pickup(), radiusKm, 20);
        if (nearby.isEmpty()) {
            return null;
        }
        Set<String> tried = redis.opsForSet().members(triedKey(trip.getId()));
        Set<Long> alreadyTried = tried == null ? Set.of()
            : tried.stream().map(Long::parseLong).collect(Collectors.toCollection(HashSet::new));

        Set<Long> available = drivers.findAllById(nearby.stream().map(NearbyDriver::driverId).toList()).stream()
            .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
            .map(Driver::getUserId)
            .collect(Collectors.toSet());

        for (NearbyDriver candidate : nearby) { // already sorted nearest first
            long id = candidate.driverId();
            if (alreadyTried.contains(id) || !available.contains(id)) {
                continue;
            }
            if (trips.existsByOfferedDriverIdAndStatusAndOfferExpiresAtAfter(id, TripStatus.REQUESTED, now)) {
                continue; // busy deciding on another rider's trip
            }
            return id;
        }
        return null;
    }

    private static String triedKey(long tripId) {
        return "trip:" + tripId + ":tried";
    }
}
