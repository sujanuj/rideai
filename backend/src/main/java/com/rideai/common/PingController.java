package com.rideai.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A tiny endpoint the frontend calls to prove it can reach the backend. */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "service", "rideai-backend",
            "status", "ok",
            "time", Instant.now().toString());
    }
}
