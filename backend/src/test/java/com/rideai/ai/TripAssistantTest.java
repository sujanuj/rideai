package com.rideai.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideai.common.GeoPoint;
import com.rideai.rides.Trip;
import com.rideai.rides.TripEvent;
import com.rideai.rides.TripStatus;

/** "Golden trips": fixed inputs with known metrics, flags and wording. */
class TripAssistantTest {

    private static final ZoneId PHOENIX = ZoneId.of("America/Phoenix");
    private static final Instant T0 = Instant.parse("2026-09-26T15:30:00Z"); // Saturday 8:30 AM in Phoenix

    private final TemplateInsightWriter template = new TemplateInsightWriter();

    /** Build a completed trip with the given offsets (in seconds after T0). */
    private static Trip trip(int estDistanceM, int estDurationS, int acceptedAt, int pickupEtaS, int arrivedAt,
                             int startedAt, int completedAt, Integer actualDistanceM) {
        Trip t = new Trip(1L, new GeoPoint(33.4242, -111.9281), new GeoPoint(33.4152, -111.8315),
            estDistanceM, estDurationS, 1500);
        ReflectionTestUtils.setField(t, "id", 42L);
        ReflectionTestUtils.setField(t, "status", TripStatus.COMPLETED);
        ReflectionTestUtils.setField(t, "requestedAt", T0);
        ReflectionTestUtils.setField(t, "acceptedAt", T0.plusSeconds(acceptedAt));
        ReflectionTestUtils.setField(t, "arrivedAt", T0.plusSeconds(arrivedAt));
        ReflectionTestUtils.setField(t, "startedAt", T0.plusSeconds(startedAt));
        ReflectionTestUtils.setField(t, "completedAt", T0.plusSeconds(completedAt));
        t.setPickupEtaS(pickupEtaS);
        t.setActualDistanceM(actualDistanceM);
        return t;
    }

    private static List<TripEvent> events(String... types) {
        return java.util.Arrays.stream(types).map(type -> new TripEvent(42L, type, Map.of())).toList();
    }

    @Test
    void smoothTrip() {
        // matched in 20 s, driver arrived on time, ride on estimate, no detour
        Trip t = trip(9_000, 900, 20, 300, 320, 380, 1_280, 9_100);
        TripMetrics m = TripMetricsCalculator.compute(t, events("RIDE_REQUESTED", "DRIVER_OFFERED", "DRIVER_ASSIGNED"),
            List.of(), PHOENIX);

        assertThat(m.flags()).containsExactly("SMOOTH_TRIP");
        assertThat(m.dayOfWeek()).isEqualTo("Saturday");
        assertThat(m.hourOfDay()).isEqualTo(8);
        assertThat(m.pickupWaitMinutes()).isEqualTo(5.0);
        assertThat(m.actualRideMinutes()).isEqualTo(15.0);
        assertThat(m.estimatedDistanceKm()).isEqualTo(9.0);

        GeneratedInsight insight = template.write(m);
        assertThat(insight.summary()).contains("smooth").contains("9 km");
        assertThat(insight.suggestions()).isEmpty();
    }

    @Test
    void lateDriverSlowMatchAndDetour() {
        // 2.5 min to match with 2 declines, ETA 4 min but took 10, ride 20 min vs 12 estimated, 30% longer route
        Trip t = trip(8_000, 720, 150, 240, 750, 800, 2_000, 10_400);
        TripMetrics m = TripMetricsCalculator.compute(t,
            events("RIDE_REQUESTED", "DRIVER_OFFERED", "DRIVER_DECLINED", "DRIVER_OFFERED", "DRIVER_DECLINED",
                "DRIVER_OFFERED", "DRIVER_ASSIGNED", "DELAY_DETECTED"),
            List.of(), PHOENIX);

        assertThat(m.offersMade()).isEqualTo(3);
        assertThat(m.offersDeclined()).isEqualTo(2);
        assertThat(m.pickupDelayMinutes()).isEqualTo(6.0);
        assertThat(m.detourPercent()).isEqualTo(30.0);
        assertThat(m.rideDelayPercent()).isCloseTo(66.7, org.assertj.core.data.Offset.offset(0.1));
        assertThat(m.flags()).contains("SLOW_MATCH", "LATE_PICKUP", "LONG_WAIT", "SLOW_RIDE", "ROUTE_DEVIATION", "DELAY_ALERT")
            .doesNotContain("SMOOTH_TRIP");

        GeneratedInsight insight = template.write(m);
        assertThat(insight.suggestions()).hasSize(3); // capped at 3
        assertThat(insight.suggestions().get(0).title()).isEqualTo("Book from a busier spot");
        assertThat(insight.model()).isEqualTo("template");
    }

    @Test
    void riderHistoryIsAveraged() {
        Trip current = trip(5_000, 600, 30, 300, 330, 360, 960, null);
        Trip pastA = trip(5_000, 600, 30, 300, 300, 330, 900, null); // driver took 4.5 min
        Trip pastB = trip(5_000, 600, 30, 300, 540, 570, 1_100, null); // driver took 8.5 min
        TripMetrics m = TripMetricsCalculator.compute(current, List.of(), List.of(pastA, pastB), PHOENIX);

        assertThat(m.riderPastTrips()).isEqualTo(2);
        assertThat(m.riderAvgPickupWaitMinutes()).isEqualTo(6.5);
        assertThat(m.actualDistanceKm()).isNull();
        assertThat(m.detourPercent()).isNull();
    }

    @Test
    void parsesClaudeToolCallAndRejectsBadReplies() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String good = """
            {"content":[{"type":"tool_use","name":"record_trip_insight","input":{
              "summary":"Your driver arrived 6 minutes late.",
              "suggestions":[{"title":"Request earlier","detail":"Pickups at 8 AM took 10 min."},
                             {"title":"","detail":"missing title is dropped"}]}}],
             "usage":{"input_tokens":400,"output_tokens":80}}""";
        GeneratedInsight parsed = ClaudeClient.parseInsight(json.readTree(good), "claude-test");
        assertThat(parsed.summary()).isEqualTo("Your driver arrived 6 minutes late.");
        assertThat(parsed.suggestions()).hasSize(1);
        assertThat(parsed.model()).isEqualTo("claude-test");

        String noTool = """
            {"content":[{"type":"text","text":"Sure! Here is JSON..."}]}""";
        assertThat(ClaudeClient.parseInsight(json.readTree(noTool), "claude-test")).isNull();

        String emptySummary = """
            {"content":[{"type":"tool_use","input":{"summary":"  ","suggestions":[]}}]}""";
        assertThat(ClaudeClient.parseInsight(json.readTree(emptySummary), "claude-test")).isNull();
    }

    @Test
    void fallsBackToTemplateWhenClaudeFails() throws Exception {
        ClaudeClient claude = org.mockito.Mockito.mock(ClaudeClient.class);
        org.mockito.Mockito.when(claude.isConfigured()).thenReturn(true);
        org.mockito.Mockito.when(claude.insight(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new RuntimeException("503 overloaded"));
        InsightService service = new InsightService(null, null, null, null, claude, template, null, null, null);

        TripMetrics m = TripMetricsCalculator.compute(trip(9_000, 900, 20, 300, 320, 380, 1_280, null),
            List.of(), List.of(), PHOENIX);
        GeneratedInsight insight = service.generate(m);

        assertThat(insight.model()).isEqualTo("template");
        org.mockito.Mockito.verify(claude, org.mockito.Mockito.times(2)).insight(m); // one retry
    }
}
