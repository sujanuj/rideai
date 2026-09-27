package com.rideai.matching;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rideai.rides.TripRepository;

/**
 * Every couple of seconds: find REQUESTED trips whose offer expired (or that are still
 * waiting for a driver to come online) and try the next driver.
 */
@Component
public class MatchingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchingScheduler.class);

    private final TripRepository trips;
    private final MatchingService matching;

    public MatchingScheduler(TripRepository trips, MatchingService matching) {
        this.trips = trips;
        this.matching = matching;
    }

    @Scheduled(fixedDelayString = "${rideai.matching.sweep-interval:2s}")
    public void retryExpiredOffers() {
        for (Long tripId : trips.findRequestedWithoutLiveOffer(Instant.now())) {
            try {
                matching.offerNext(tripId);
            } catch (RuntimeException e) {
                log.warn("Matching retry failed for trip {}", tripId, e);
            }
        }
    }
}
