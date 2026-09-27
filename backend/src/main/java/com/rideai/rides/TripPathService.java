package com.rideai.rides;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.rideai.common.GeoPoint;

/**
 * Records the path actually driven during a ride (from GPS points on Kafka) so we can compare
 * it with the estimate: a detour shows up as actual distance well above the estimated distance.
 *
 *   trip:{id}:path       Redis list of "lat,lng"
 *   trip:{id}:odometer   running total in metres
 */
@Service
public class TripPathService {

    private static final Duration KEEP = Duration.ofDays(1);
    /** Ignore GPS jumps bigger than this between two points (bad fixes). */
    private static final double MAX_JUMP_METERS = 2_000;

    private final StringRedisTemplate redis;

    public TripPathService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void append(long tripId, GeoPoint point) {
        String pathKey = pathKey(tripId);
        String last = redis.opsForList().index(pathKey, -1);
        if (last != null) {
            double step = parse(last).distanceMetersTo(point);
            if (step > MAX_JUMP_METERS) {
                return;
            }
            redis.opsForValue().increment(odometerKey(tripId), step);
        }
        redis.opsForList().rightPush(pathKey, point.lat() + "," + point.lng());
        redis.expire(pathKey, KEEP);
        redis.expire(odometerKey(tripId), KEEP);
    }

    public Optional<Integer> distanceMeters(long tripId) {
        String value = redis.opsForValue().get(odometerKey(tripId));
        return value == null ? Optional.empty() : Optional.of((int) Math.round(Double.parseDouble(value)));
    }

    public List<GeoPoint> path(long tripId) {
        List<String> raw = redis.opsForList().range(pathKey(tripId), 0, -1);
        return raw == null ? List.of() : raw.stream().map(TripPathService::parse).toList();
    }

    private static GeoPoint parse(String s) {
        String[] parts = s.split(",");
        return new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }

    private static String pathKey(long tripId) {
        return "trip:" + tripId + ":path";
    }

    private static String odometerKey(long tripId) {
        return "trip:" + tripId + ":odometer";
    }
}
