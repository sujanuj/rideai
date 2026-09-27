package com.rideai.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideai.common.ApiException;
import com.rideai.realtime.RealtimeNotifier;
import com.rideai.rides.Trip;
import com.rideai.rides.TripRepository;
import com.rideai.rides.TripService;
import com.rideai.rides.TripStatus;

/**
 * The AI Trip Assistant.
 *   1. On TRIP_COMPLETED (from Kafka), compute the trip's metrics in Java.
 *   2. Ask Claude to explain them (structured output, validated). One retry, then fall back
 *      to the rule-based template, so the rider always gets a summary.
 *   3. Store it once (idempotent) and tell the rider's app over WebSocket.
 * Riders can then ask follow-up questions about that trip.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final int MAX_QUESTIONS_PER_TRIP = 10;

    private final TripRepository trips;
    private final TripService tripService;
    private final TripInsightRepository insights;
    private final TripMetricsCalculator calculator;
    private final ClaudeClient claude;
    private final TemplateInsightWriter template;
    private final RealtimeNotifier notifier;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public InsightService(TripRepository trips, TripService tripService, TripInsightRepository insights,
                          TripMetricsCalculator calculator, ClaudeClient claude, TemplateInsightWriter template,
                          RealtimeNotifier notifier, StringRedisTemplate redis, ObjectMapper json) {
        this.trips = trips;
        this.tripService = tripService;
        this.insights = insights;
        this.calculator = calculator;
        this.claude = claude;
        this.template = template;
        this.notifier = notifier;
        this.redis = redis;
        this.json = json;
    }

    public record InsightResponse(long tripId, String summary, List<Map<String, String>> suggestions,
                                  List<String> flags, TripMetrics metrics, String model, boolean aiGenerated) {
    }

    public record AnswerResponse(String answer, boolean aiGenerated, int questionsLeft) {
    }

    /**
     * Deliberately not one big transaction: the Claude call can take seconds, and we don't want
     * to hold a database connection open while waiting. Each repository call commits on its own.
     */
    public void generateFor(long tripId) {
        if (insights.existsById(tripId)) {
            return; // already done (e.g. Kafka redelivered the event)
        }
        Trip trip = trips.findById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.COMPLETED) {
            return;
        }
        TripMetrics metrics = calculator.forTrip(trip);
        GeneratedInsight insight = generate(metrics);
        insights.save(new TripInsight(tripId, insight, metrics));
        log.info("Insight for trip {} written by {} (flags {})", tripId, insight.model(), metrics.flags());
        notifier.insightReady(tripId);
    }

    GeneratedInsight generate(TripMetrics metrics) {
        if (claude.isConfigured()) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    GeneratedInsight fromClaude = claude.insight(metrics);
                    if (fromClaude != null) {
                        return fromClaude;
                    }
                    log.warn("Claude reply for trip {} was unusable (attempt {})", metrics.tripId(), attempt);
                } catch (Exception e) {
                    log.warn("Claude call for trip {} failed (attempt {}): {}", metrics.tripId(), attempt, e.getMessage());
                }
            }
        }
        return template.write(metrics);
    }

    @Transactional(readOnly = true)
    public InsightResponse get(long tripId, long userId) {
        tripService.loadForUser(tripId, userId); // access check
        TripInsight i = insights.findById(tripId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSIGHT_PENDING", "The trip summary isn't ready yet"));
        return toResponse(i);
    }

    public AnswerResponse ask(long tripId, long userId, String question) {
        Trip trip = tripService.loadForUser(tripId, userId);
        if (trip.getRiderId() != userId) {
            throw ApiException.forbidden("Only the rider can chat about this trip");
        }
        Optional<TripInsight> insight = insights.findById(tripId);
        if (insight.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "INSIGHT_PENDING", "Ask again once the trip summary is ready");
        }

        String counterKey = "trip:" + tripId + ":questions";
        Long asked = redis.opsForValue().increment(counterKey);
        redis.expire(counterKey, Duration.ofDays(7));
        int left = MAX_QUESTIONS_PER_TRIP - (asked == null ? 0 : asked.intValue());
        if (left < 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "QUESTION_LIMIT",
                "You've asked " + MAX_QUESTIONS_PER_TRIP + " questions about this trip");
        }

        if (!claude.isConfigured()) {
            return new AnswerResponse(
                "Chat needs an Anthropic API key on the server (ANTHROPIC_API_KEY). Here's the summary: "
                    + insight.get().getSummary(), false, left);
        }
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("metrics", insight.get().getMetrics());
            context.put("summary", insight.get().getSummary());
            context.put("suggestions", insight.get().getSuggestions());
            context.put("pickup", trip.pickup());
            context.put("dropoff", trip.dropoff());
            String answer = claude.chat(json.writeValueAsString(context), question.trim());
            return new AnswerResponse(answer.isEmpty() ? "Sorry, I couldn't answer that." : answer, true, left);
        } catch (Exception e) {
            log.warn("Claude chat failed for trip {}: {}", tripId, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", "The assistant is unavailable right now");
        }
    }

    private static InsightResponse toResponse(TripInsight i) {
        return new InsightResponse(i.getTripId(), i.getSummary(), i.getSuggestions(), i.getFlags(), i.getMetrics(),
            i.getModel(), !TemplateInsightWriter.MODEL.equals(i.getModel()));
    }
}
