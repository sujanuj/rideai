package com.rideai.drivers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rideai.common.ApiException;
import com.rideai.common.GeoPoint;
import com.rideai.drivers.DriverLocationService.NearbyDriver;
import com.rideai.events.DriverLocationMessage;
import com.rideai.events.KafkaEventPublisher;
import com.rideai.rides.TripRepository;
import com.rideai.rides.TripStatus;

@Service
public class DriverService {

    private final DriverRepository drivers;
    private final DriverLocationService locations;
    private final TripRepository trips;
    private final SimpMessagingTemplate ws;
    private final KafkaEventPublisher kafka;

    public DriverService(DriverRepository drivers, DriverLocationService locations, TripRepository trips,
                         SimpMessagingTemplate ws, KafkaEventPublisher kafka) {
        this.drivers = drivers;
        this.locations = locations;
        this.trips = trips;
        this.ws = ws;
        this.kafka = kafka;
    }

    @Transactional(readOnly = true)
    public Driver get(long driverId) {
        return drivers.findById(driverId).orElseThrow(() -> ApiException.notFound("Driver"));
    }

    /** Go online (AVAILABLE) or offline. You can't go offline in the middle of a trip. */
    @Transactional
    public Driver setStatus(long driverId, DriverStatus wanted, GeoPoint position) {
        Driver driver = get(driverId);
        if (wanted == DriverStatus.ON_TRIP) {
            throw ApiException.badRequest("INVALID_STATUS", "ON_TRIP is set automatically when you accept a ride");
        }
        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            throw ApiException.conflict("ON_TRIP", "Finish or cancel your current trip first");
        }
        driver.setStatus(wanted);
        if (wanted == DriverStatus.AVAILABLE) {
            if (position != null) {
                locations.update(driverId, position, true);
            } else {
                locations.markAvailable(driverId);
            }
        } else {
            locations.goOffline(driverId);
        }
        return driver;
    }

    /**
     * A GPS update. Free drivers are re-indexed for nearby search. Drivers on a trip also:
     *  - push their position to the rider's app over WebSocket (low latency, in-process), and
     *  - publish it to Kafka, where the trip monitor records the route and watches for delays.
     */
    @Transactional(readOnly = true)
    public void updateLocation(long driverId, GeoPoint position) {
        Driver driver = get(driverId);
        if (driver.getStatus() == DriverStatus.OFFLINE) {
            throw ApiException.conflict("OFFLINE", "Go online before sending your location");
        }
        locations.update(driverId, position, driver.getStatus() == DriverStatus.AVAILABLE);

        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            trips.findFirstByDriverIdAndStatusIn(driverId, TripStatus.ACTIVE).ifPresent(trip -> {
                ws.convertAndSend("/topic/trips/" + trip.getId(),
                    Map.of("type", "location", "lat", position.lat(), "lng", position.lng()));
                kafka.driverLocation(new DriverLocationMessage(driverId, trip.getId(), trip.getStatus().name(),
                    position.lat(), position.lng(), Instant.now()));
            });
        }
    }

    /** Called when a driver accepts a trip. */
    @Transactional
    public void startTrip(long driverId) {
        get(driverId).setStatus(DriverStatus.ON_TRIP);
        locations.markUnavailable(driverId);
    }

    /** Called when a driver's trip completes or is cancelled: back in the pool. */
    @Transactional
    public void finishTrip(long driverId) {
        Driver driver = get(driverId);
        driver.setStatus(DriverStatus.AVAILABLE);
        locations.markAvailable(driverId);
    }

    public List<NearbyDriver> nearby(GeoPoint center) {
        return locations.findNearby(center, 5, 25);
    }
}
