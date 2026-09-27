package com.rideai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 0 gate: the app starts against real Postgres, Redis and Kafka,
 * Flyway creates the schema, and the health endpoint reports UP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class RideAiApplicationTests {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void pingAnswers() {
        ResponseEntity<String> response = rest.getForEntity("/api/ping", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"ok\"");
    }

    @Test
    void flywayCreatedTheSchema() {
        Integer tables = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                + "AND table_name IN ('users','drivers','trips','trip_events','trip_insights')",
            Integer.class);
        assertThat(tables).isEqualTo(5);
    }
}
