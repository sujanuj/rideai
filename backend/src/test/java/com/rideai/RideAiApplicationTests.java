package com.rideai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Phase 0 gate: the app starts against real Postgres, Redis and Kafka,
 * Flyway creates the schema, and the health endpoint reports UP.
 */
class RideAiApplicationTests extends IntegrationTest {

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void pingAnswers() throws Exception {
        mvc.perform(get("/api/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
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
