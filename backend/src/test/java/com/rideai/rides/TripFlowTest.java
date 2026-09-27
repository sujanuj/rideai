package com.rideai.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideai.IntegrationTest;

/** End-to-end: request → nearest driver gets the offer → accept → arrive → start → complete. */
class TripFlowTest extends IntegrationTest {

    // Around ASU Tempe
    private static final Map<String, Double> PICKUP = Map.of("lat", 33.4242, "lng", -111.9281);
    private static final Map<String, Double> DROPOFF = Map.of("lat", 33.4152, "lng", -111.8315);
    private static final Map<String, Object> TRIP = Map.of("pickup", PICKUP, "dropoff", DROPOFF);

    @Test
    void estimateReturnsFareAndRoute() throws Exception {
        Account rider = registerRider();
        postAs(rider, "/api/trips/estimate", TRIP)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fareCents").isNumber())
            .andExpect(jsonPath("$.distanceMeters").isNumber())
            .andExpect(jsonPath("$.route.length()").value(2))
            .andExpect(jsonPath("$.routeSource").value("estimate"));
    }

    @Test
    void fullTripHappyPath() throws Exception {
        Account rider = registerRider();
        Account driver = registerDriver();
        goOnline(driver, 33.4250, -111.9290); // ~120 m from pickup

        JsonNode trip = read(postAs(rider, "/api/trips", TRIP)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andReturn());
        long tripId = trip.path("id").asLong();

        // The nearest driver sees the offer
        getAs(driver, "/api/drivers/me/offer")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(tripId));

        // A rider can't book a second ride while one is active
        postAs(rider, "/api/trips", TRIP)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TRIP_IN_PROGRESS"));

        postAs(driver, "/api/trips/" + tripId + "/accept", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.driver.vehicle").value("Toyota Prius"));

        // Accepting twice fails: the conditional UPDATE only matches once
        postAs(driver, "/api/trips/" + tripId + "/accept", null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFER_UNAVAILABLE"));

        // Can't skip steps
        postAs(driver, "/api/trips/" + tripId + "/complete", null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        getAs(driver, "/api/drivers/me").andExpect(jsonPath("$.status").value("ON_TRIP"));

        postAs(driver, "/api/trips/" + tripId + "/arrive", null).andExpect(jsonPath("$.status").value("ARRIVED"));
        postAs(driver, "/api/trips/" + tripId + "/start", null).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAs(driver, "/api/trips/" + tripId + "/complete", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // Rider sees the result; driver is free again
        getAs(rider, "/api/trips/" + tripId).andExpect(jsonPath("$.status").value("COMPLETED"));
        getAs(driver, "/api/drivers/me").andExpect(jsonPath("$.status").value("AVAILABLE"));
        getAs(rider, "/api/trips/current").andExpect(status().isNoContent());

        // The timeline recorded every step, in order
        JsonNode timeline = read(getAs(rider, "/api/trips/" + tripId + "/timeline").andReturn());
        List<String> types = timeline.findValuesAsText("type");
        assertThat(types).containsExactly(
            "RIDE_REQUESTED", "DRIVER_OFFERED", "DRIVER_ASSIGNED",
            "DRIVER_ARRIVED", "TRIP_STARTED", "TRIP_COMPLETED");
    }

    @Test
    void declinedOfferGoesToTheNextNearestDriver() throws Exception {
        Account rider = registerRider();
        Account near = registerDriver();
        Account farther = registerDriver();
        goOnline(near, 33.4245, -111.9283);    // ~30 m away
        goOnline(farther, 33.4300, -111.9350); // ~900 m away

        long tripId = read(postAs(rider, "/api/trips", TRIP).andReturn()).path("id").asLong();

        getAs(near, "/api/drivers/me/offer").andExpect(jsonPath("$.id").value(tripId));
        getAs(farther, "/api/drivers/me/offer").andExpect(status().isNoContent());

        postAs(near, "/api/trips/" + tripId + "/decline", null).andExpect(status().isNoContent());

        getAs(near, "/api/drivers/me/offer").andExpect(status().isNoContent());
        getAs(farther, "/api/drivers/me/offer").andExpect(jsonPath("$.id").value(tripId));

        // The driver who declined can no longer accept
        postAs(near, "/api/trips/" + tripId + "/accept", null).andExpect(status().isConflict());
        postAs(farther, "/api/trips/" + tripId + "/accept", null).andExpect(status().isOk());
    }

    @Test
    void offlineAndFarAwayDriversAreNotOffered() throws Exception {
        Account rider = registerRider();
        Account faraway = registerDriver();
        goOnline(faraway, 33.6000, -112.2000); // ~30 km away, outside the 5 km radius

        long tripId = read(postAs(rider, "/api/trips", TRIP).andReturn()).path("id").asLong();

        getAs(faraway, "/api/drivers/me/offer").andExpect(status().isNoContent());
        getAs(rider, "/api/trips/" + tripId)
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.driver").doesNotExist());
    }

    @Test
    void riderCanCancelAndStrangersCannotSeeTheTrip() throws Exception {
        Account rider = registerRider();
        Account stranger = registerRider();
        long tripId = read(postAs(rider, "/api/trips", TRIP).andReturn()).path("id").asLong();

        getAs(stranger, "/api/trips/" + tripId).andExpect(status().isNotFound());

        postAs(rider, "/api/trips/" + tripId + "/cancel", Map.of("reason", "Changed my plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("Changed my plans"));
    }

    @Test
    void nearbyShowsOnlineDriversWithoutIdentities() throws Exception {
        Account rider = registerRider();
        Account driver = registerDriver();
        goOnline(driver, 33.4250, -111.9290);

        getAs(rider, "/api/drivers/nearby?lat=33.4242&lng=-111.9281")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].lat").isNumber())
            .andExpect(jsonPath("$[0].driverId").doesNotExist());
    }
}
