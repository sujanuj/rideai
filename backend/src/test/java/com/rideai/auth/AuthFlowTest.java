package com.rideai.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.rideai.IntegrationTest;

class AuthFlowTest extends IntegrationTest {

    @Test
    void registerLoginAndMe() throws Exception {
        String body = json.writeValueAsString(Map.of(
            "email", "Maya@Example.com",
            "password", "correct-horse",
            "fullName", "Maya Rider",
            "role", "RIDER"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("maya@example.com"));

        // same email again (different case) is rejected
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        String token = read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "maya@example.com", "password", "correct-horse"))))
            .andExpect(status().isOk())
            .andReturn()).path("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Maya Rider"))
            .andExpect(jsonPath("$.role").value("RIDER"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        registerRider();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "nobody@test.dev", "password", "whatever1"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void validationErrorsListTheBadFields() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "not-an-email", "password", "short",
                    "fullName", "X", "role", "RIDER"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fields.email").exists())
            .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void driversMustGiveAVehicle() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "d@test.dev", "password", "password123",
                    "fullName", "No Car", "role", "DRIVER"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VEHICLE_REQUIRED"));
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ridersCannotUseDriverEndpoints() throws Exception {
        Account rider = registerRider();
        getAs(rider, "/api/drivers/me").andExpect(status().isForbidden());
    }
}
