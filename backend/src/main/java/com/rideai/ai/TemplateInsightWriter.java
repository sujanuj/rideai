package com.rideai.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.rideai.ai.GeneratedInsight.Suggestion;

/**
 * Rule-based insight writer. Used when no Anthropic API key is configured, and as the
 * fallback whenever the Claude call fails or returns something invalid.
 */
@Component
public class TemplateInsightWriter {

    public static final String MODEL = "template";

    public GeneratedInsight write(TripMetrics m) {
        List<Suggestion> suggestions = new ArrayList<>();
        List<String> flags = m.flags();

        if (flags.contains("SLOW_MATCH")) {
            suggestions.add(new Suggestion("Book from a busier spot",
                String.format(Locale.US, "It took %s min to find a driver%s. Pickups on main roads get matched faster.",
                    fmt(m.minutesToFindDriver()),
                    m.offersDeclined() > 0 ? " (" + m.offersDeclined() + " declined)" : "")));
        }
        if (flags.contains("LONG_WAIT") || flags.contains("LATE_PICKUP")) {
            suggestions.add(new Suggestion("Request a few minutes earlier",
                String.format(Locale.US, "Your driver took %s min to reach you%s. Around %s, leave a little buffer.",
                    fmt(m.pickupWaitMinutes()),
                    m.pickupEtaMinutes() != null ? " versus about " + fmt(m.pickupEtaMinutes()) + " min estimated" : "",
                    hourLabel(m.hourOfDay()))));
        }
        if (flags.contains("ROUTE_DEVIATION")) {
            suggestions.add(new Suggestion("Check the route with your driver",
                String.format(Locale.US, "The ride covered %s km against %s km planned (%s%% longer).",
                    fmt(m.actualDistanceKm()), fmt(m.estimatedDistanceKm()), fmt(m.detourPercent()))));
        }
        if (flags.contains("SLOW_RIDE") || flags.contains("DELAY_ALERT")) {
            suggestions.add(new Suggestion("Allow extra travel time at this hour",
                String.format(Locale.US, "The ride took %s min instead of about %s min.",
                    fmt(m.actualRideMinutes()), fmt(m.estimatedRideMinutes()))));
        }
        if (flags.contains("LONG_BOARDING") && suggestions.size() < 3) {
            suggestions.add(new Suggestion("Be ready at the pickup point",
                String.format(Locale.US, "The driver waited %s min before the ride started.", fmt(m.boardingMinutes()))));
        }

        String summary;
        if (flags.contains("SMOOTH_TRIP")) {
            summary = String.format(Locale.US,
                "A smooth %s km ride: your driver arrived in %s min and the trip took %s min, right on estimate.",
                fmt(m.estimatedDistanceKm()), fmt(m.pickupWaitMinutes()), fmt(m.actualRideMinutes()));
        } else {
            summary = String.format(Locale.US,
                "Your %s km ride took %s min from request to drop-off. The driver took %s min to arrive and the ride itself %s min (about %s min estimated).",
                fmt(m.estimatedDistanceKm()), fmt(m.totalMinutesRequestToDropoff()), fmt(m.pickupWaitMinutes()),
                fmt(m.actualRideMinutes()), fmt(m.estimatedRideMinutes()));
        }
        return new GeneratedInsight(summary, suggestions.stream().limit(3).toList(), MODEL);
    }

    private static String fmt(Double value) {
        if (value == null) {
            return "?";
        }
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.format(Locale.US, "%.1f", value);
    }

    private static String hourLabel(int hour) {
        int h = hour % 12 == 0 ? 12 : hour % 12;
        return h + (hour < 12 ? " AM" : " PM");
    }
}
