package com.rideai.realtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.rideai.events.TripEventMessage;
import com.rideai.rides.TripDtos.TripResponse;
import com.rideai.rides.TripService;

/**
 * Pushes changes to connected apps the moment they're committed:
 *   /topic/trips/{id}          {"type":"trip", "trip": {...}}  on every status change
 *   /user/{driverId}/queue/offers  the trip, when it's offered to that driver
 * Driver positions are pushed separately by DriverService ({"type":"location", ...}).
 */
@Component
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final SimpMessagingTemplate ws;
    private final TripService trips;

    public RealtimeNotifier(SimpMessagingTemplate ws, TripService trips) {
        this.ws = ws;
        this.trips = trips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripEvent(TripEventMessage event) {
        try {
            TripResponse trip = trips.view(event.tripId());
            ws.convertAndSend("/topic/trips/" + event.tripId(), Map.of("type", "trip", "event", event.type(), "trip", trip));

            if ("DRIVER_OFFERED".equals(event.type()) && event.payload().get("driverId") instanceof Number driverId) {
                ws.convertAndSendToUser(String.valueOf(driverId.longValue()), "/queue/offers", trip);
            }
        } catch (RuntimeException e) {
            log.warn("Realtime push failed for trip {}: {}", event.tripId(), e.getMessage());
        }
    }

    /** Tell the rider their AI trip summary is ready. */
    public void insightReady(long tripId) {
        ws.convertAndSend("/topic/trips/" + tripId, Map.of("type", "insight"));
    }
}
