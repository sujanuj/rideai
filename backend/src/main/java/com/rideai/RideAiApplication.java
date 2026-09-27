package com.rideai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RideAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideAiApplication.class, args);
    }
}
