package com.rideai.ai;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideai.events.Deduplicator;
import com.rideai.events.Topics;
import com.rideai.events.TripEventMessage;

/** Kicks off the AI Trip Assistant when a trip completes, off the request path. */
@Component
public class AiListener {

    private final InsightService insights;
    private final Deduplicator dedup;
    private final ObjectMapper json;

    public AiListener(InsightService insights, Deduplicator dedup, ObjectMapper json) {
        this.insights = insights;
        this.dedup = dedup;
        this.json = json;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = Topics.GROUP_AI)
    public void onTripEvent(String raw) throws JsonProcessingException {
        TripEventMessage event = json.readValue(raw, TripEventMessage.class);
        if ("TRIP_COMPLETED".equals(event.type()) && dedup.firstTime(Topics.GROUP_AI, event.eventId())) {
            insights.generateFor(event.tripId());
        }
    }
}
