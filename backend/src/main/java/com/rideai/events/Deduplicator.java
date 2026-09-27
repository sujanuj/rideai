package com.rideai.events;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka guarantees "at least once", so the same event can arrive twice (e.g. after a consumer
 * restart). Each consumer group calls firstTime() and skips events it has already handled.
 */
@Component
public class Deduplicator {

    private static final Duration REMEMBER_FOR = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    public Deduplicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** True the first time this (group, eventId) pair is seen; false for repeats. */
    public boolean firstTime(String group, String eventId) {
        Boolean isNew = redis.opsForValue().setIfAbsent("processed:" + group + ":" + eventId, "1", REMEMBER_FOR);
        return Boolean.TRUE.equals(isNew);
    }
}
