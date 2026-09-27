package com.rideai.ai;

import java.util.List;

/**
 * The facts about one completed trip, computed in Java. The LLM only explains these numbers;
 * it never computes them, which keeps the AI from inventing figures.
 * Durations are in minutes, distances in km, money in dollars. Null means "not known".
 */
public record TripMetrics(
    long tripId,
    String requestedAt,          // e.g. "Saturday 8:42 PM"
    int hourOfDay,
    String dayOfWeek,
    Double minutesToFindDriver,
    int offersMade,
    int offersDeclined,
    Double pickupEtaMinutes,     // what we estimated when the driver accepted
    Double pickupWaitMinutes,    // accepted -> driver arrived
    Double pickupDelayMinutes,   // wait minus estimate (positive = late)
    Double boardingMinutes,      // driver arrived -> trip started
    Double estimatedRideMinutes,
    Double actualRideMinutes,
    Double rideDelayPercent,     // positive = slower than estimated
    Double estimatedDistanceKm,
    Double actualDistanceKm,
    Double detourPercent,        // positive = drove further than the planned route
    Double fareDollars,
    Double farePerKm,
    int delayAlerts,
    Double totalMinutesRequestToDropoff,
    int riderPastTrips,
    Double riderAvgPickupWaitMinutes,
    List<String> flags) {
}
