package com.rideai.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideai.common.GeoPoint;
import com.rideai.events.DriverLocationMessage;
import com.rideai.events.Topics;
import com.rideai.rides.Trip;
import com.rideai.rides.TripEventService;
import com.rideai.rides.TripPathService;
import com.rideai.rides.TripRepository;
import com.rideai.rides.TripStatus;

/**
 * Consumes driver GPS points from Kafka for trips in progress and:
 *  - records the route actually driven (for detour detection), and
 *  - raises DELAY_DETECTED when the driver is running well behind the estimate,
 *    once per phase ("pickup" or "trip"). The AI assistant uses these events later.
 */
@Component
public class TripMonitor {

    private static final Logger log = LoggerFactory.getLogger(TripMonitor.class);

    private final ObjectMapper json;
    private final TripRepository trips;
    private final TripPathService paths;
    private final TripEventService events;
    private final StringRedisTemplate redis;
    private final Duration threshold;

    public TripMonitor(ObjectMapper json, TripRepository trips, TripPathService paths, TripEventService events,
                       StringRedisTemplate redis,
                       @Value("${rideai.monitor.delay-threshold:5m}") Duration threshold) {
        this.json = json;
        this.trips = trips;
        this.paths = paths;
        this.events = events;
        this.redis = redis;
        this.threshold = threshold;
    }

    @KafkaListener(topics = Topics.DRIVER_LOCATIONS, groupId = Topics.GROUP_TRIP_MONITOR)
    @Transactional
    public void onLocation(String raw) throws JsonProcessingException {
        DriverLocationMessage point = json.readValue(raw, DriverLocationMessage.class);
        if (TripStatus.IN_PROGRESS.name().equals(point.tripStatus())) {
            paths.append(point.tripId(), new GeoPoint(point.lat(), point.lng()));
        }
        trips.findById(point.tripId()).ifPresent(trip -> checkForDelay(trip, point.at()));
    }

    void checkForDelay(Trip trip, Instant now) {
        String phase;
        Instant expected;
        if (trip.getStatus() == TripStatus.ACCEPTED && trip.getAcceptedAt() != null && trip.getPickupEtaS() != null) {
            phase = "pickup";
            expected = trip.getAcceptedAt().plusSeconds(trip.getPickupEtaS());
        } else if (trip.getStatus() == TripStatus.IN_PROGRESS && trip.getStartedAt() != null && trip.getEstDurationS() != null) {
            phase = "trip";
            expected = trip.getStartedAt().plusSeconds(trip.getEstDurationS());
        } else {
            return;
        }

        Duration late = Duration.between(expected, now);
        if (late.compareTo(threshold) < 0) {
            return;
        }
        // Report each phase once, even if many late GPS points arrive (or Kafka redelivers).
        Boolean first = redis.opsForValue().setIfAbsent("trip:" + trip.getId() + ":delay:" + phase, "1", Duration.ofDays(1));
        if (Boolean.TRUE.equals(first)) {
            long minutesLate = late.toMinutes();
            events.record(trip, "DELAY_DETECTED", Map.of("phase", phase, "minutesLate", minutesLate));
            log.info("Trip {} is {} min late ({})", trip.getId(), minutesLate, phase);
        }
    }
}
