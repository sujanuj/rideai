package com.rideai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts real Postgres (with PostGIS), Redis and Kafka in Docker for tests.
 * @ServiceConnection tells Spring Boot to point its datasource / redis / kafka
 * settings at these containers automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        DockerImageName postgis = DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer<>(postgis);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
    }
}
