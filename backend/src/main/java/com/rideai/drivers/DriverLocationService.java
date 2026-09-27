package com.rideai.drivers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import com.rideai.common.GeoPoint;

/**
 * Live driver positions live in Redis, not Postgres: they change every few seconds
 * and we need "who is near this point?" answered in milliseconds.
 *
 * Keys:
 *   drivers:available        GEO set (a sorted set scored by geohash) of drivers free to take a ride
 *   driver:{id}:loc          hash {lat, lng} with the latest position of any online driver
 *   driver:{id}:alive        heartbeat key that expires if the driver stops sending updates
 */
@Service
public class DriverLocationService {

    static final String AVAILABLE_KEY = "drivers:available";
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public DriverLocationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Record a driver's position; if they're free, also (re)index them for nearby search. */
    public void update(long driverId, GeoPoint position, boolean available) {
        redis.opsForHash().putAll(locKey(driverId), Map.of(
            "lat", String.valueOf(position.lat()),
            "lng", String.valueOf(position.lng())));
        redis.opsForValue().set(aliveKey(driverId), "1", HEARTBEAT_TTL);
        if (available) {
            // Redis GEO takes (longitude, latitude): x first, then y.
            redis.opsForGeo().add(AVAILABLE_KEY, new Point(position.lng(), position.lat()), String.valueOf(driverId));
        }
    }

    public void markAvailable(long driverId) {
        position(driverId).ifPresent(p -> redis.opsForGeo()
            .add(AVAILABLE_KEY, new Point(p.lng(), p.lat()), String.valueOf(driverId)));
    }

    public void markUnavailable(long driverId) {
        redis.opsForZSet().remove(AVAILABLE_KEY, String.valueOf(driverId));
    }

    public void goOffline(long driverId) {
        markUnavailable(driverId);
        redis.delete(List.of(locKey(driverId), aliveKey(driverId)));
    }

    public Optional<GeoPoint> position(long driverId) {
        Map<Object, Object> loc = redis.opsForHash().entries(locKey(driverId));
        if (loc.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(
            Double.parseDouble((String) loc.get("lat")),
            Double.parseDouble((String) loc.get("lng"))));
    }

    /**
     * GEOSEARCH drivers:available FROMLONLAT lng lat BYRADIUS r km ASC COUNT limit WITHCOORD WITHDIST
     * Drivers whose heartbeat expired (app closed, phone died) are skipped and cleaned up.
     */
    public List<NearbyDriver> findNearby(GeoPoint center, double radiusKm, int limit) {
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(
            AVAILABLE_KEY,
            GeoReference.fromCoordinate(center.lng(), center.lat()),
            new Distance(radiusKm, Metrics.KILOMETERS),
            GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().includeDistance().sortAscending().limit(limit));
        if (results == null) {
            return List.of();
        }

        return results.getContent().stream()
            .map(this::toNearby)
            .filter(d -> {
                boolean alive = Boolean.TRUE.equals(redis.hasKey(aliveKey(d.driverId())));
                if (!alive) {
                    markUnavailable(d.driverId());
                }
                return alive;
            })
            .toList();
    }

    private NearbyDriver toNearby(GeoResult<GeoLocation<String>> r) {
        GeoLocation<String> loc = r.getContent();
        return new NearbyDriver(
            Long.parseLong(loc.getName()),
            new GeoPoint(loc.getPoint().getY(), loc.getPoint().getX()),
            r.getDistance().getValue() * 1000);
    }

    private static String locKey(long driverId) {
        return "driver:" + driverId + ":loc";
    }

    private static String aliveKey(long driverId) {
        return "driver:" + driverId + ":alive";
    }

    public record NearbyDriver(long driverId, GeoPoint position, double distanceMeters) {
    }
}
