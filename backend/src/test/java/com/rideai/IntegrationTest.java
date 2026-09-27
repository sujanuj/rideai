package com.rideai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for tests that run the whole app against real Postgres, Redis and Kafka
 * (Testcontainers). All subclasses share one Spring context, so containers start once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
    "rideai.routing.enabled=false",       // no internet calls in tests
    "rideai.ai.api-key=",                 // never call Claude from tests, even if .env has a key
    "sentry.dsn=",
    "rideai.matching.sweep-interval=500ms"
})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetWorld() {
        // Each test starts with no drivers online and no open trips left over from other tests,
        // otherwise the matching scheduler could hand an old trip to this test's driver.
        jdbc.update("UPDATE trips SET status = 'CANCELLED', cancelled_at = now(), offered_driver_id = NULL, "
            + "offer_expires_at = NULL WHERE status IN ('REQUESTED', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS')");
        jdbc.update("UPDATE drivers SET status = 'OFFLINE'");
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    // ---------- helpers ----------

    public record Account(long id, String token) {
    }

    protected Account registerRider() throws Exception {
        return register(Map.of(
            "email", "rider-" + UUID.randomUUID() + "@test.dev",
            "password", "password123",
            "fullName", "Test Rider",
            "role", "RIDER"));
    }

    protected Account registerDriver() throws Exception {
        return register(Map.of(
            "email", "driver-" + UUID.randomUUID() + "@test.dev",
            "password", "password123",
            "fullName", "Test Driver",
            "role", "DRIVER",
            "vehicle", "Toyota Prius",
            "plate", "ABC123"));
    }

    private Account register(Map<String, Object> body) throws Exception {
        JsonNode res = read(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andReturn());
        return new Account(res.path("user").path("id").asLong(), res.path("token").asText());
    }

    /** Put a driver online at the given position. */
    protected void goOnline(Account driver, double lat, double lng) throws Exception {
        perform(put("/api/drivers/me/status"), driver, Map.of(
            "status", "AVAILABLE",
            "position", Map.of("lat", lat, "lng", lng)));
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, Account who, Object body) throws Exception {
        if (who != null) {
            request.header("Authorization", "Bearer " + who.token());
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    protected ResultActions postAs(Account who, String path, Object body) throws Exception {
        return perform(post(path), who, body);
    }

    protected ResultActions getAs(Account who, String path) throws Exception {
        return perform(get(path), who, null);
    }

    /** Matching runs asynchronously (Kafka), so wait for the offer to reach the driver. */
    protected void awaitOffer(Account driver, long tripId) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
            getAs(driver, "/api/drivers/me/offer")
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(tripId)));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? json.nullNode() : json.readTree(body);
    }
}
