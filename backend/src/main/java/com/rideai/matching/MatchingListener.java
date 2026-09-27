package com.rideai.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideai.events.Deduplicator;
import com.rideai.events.Topics;
import com.rideai.events.TripEventMessage;

/**
 * Asynchronous matching: the rider's POST /api/trips returns as soon as the trip is saved,
 * and this consumer finds a driver in the background.
 */
@Component
public class MatchingListener {

    private static final Logger log = LoggerFactory.getLogger(MatchingListener.class);

    private final MatchingService matching;
    private final Deduplicator dedup;
    private final ObjectMapper json;

    public MatchingListener(MatchingService matching, Deduplicator dedup, ObjectMapper json) {
        this.matching = matching;
        this.dedup = dedup;
        this.json = json;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = Topics.GROUP_MATCHING)
    public void onTripEvent(String raw) throws JsonProcessingException {
        TripEventMessage event = json.readValue(raw, TripEventMessage.class);
        boolean needsDriver = "RIDE_REQUESTED".equals(event.type()) || "DRIVER_DECLINED".equals(event.type());
        if (!needsDriver || !dedup.firstTime(Topics.GROUP_MATCHING, event.eventId())) {
            return;
        }
        log.debug("Matching trip {} after {}", event.tripId(), event.type());
        matching.offerNext(event.tripId());
    }
}
