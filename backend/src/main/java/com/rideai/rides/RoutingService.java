package com.rideai.rides;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideai.common.GeoPoint;

/**
 * Road distance, duration and the route line between two points.
 * Uses OSRM (free, OpenStreetMap-based). If OSRM is disabled, slow or down,
 * falls back to a straight-line estimate so booking a ride never breaks.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    /** Roads are longer than the straight line; 1.35 is a typical city detour factor. */
    private static final double DETOUR_FACTOR = 1.35;
    /** ~32 km/h average city speed. */
    private static final double CITY_SPEED_MPS = 8.9;
    private static final int MAX_ROUTE_POINTS = 400;

    private final RestClient osrm;
    private final boolean enabled;

    public RoutingService(RestClient.Builder builder,
                          @Value("${rideai.routing.osrm-url:https://router.project-osrm.org}") String osrmUrl,
                          @Value("${rideai.routing.enabled:true}") boolean enabled) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(3));
        this.osrm = builder.baseUrl(osrmUrl).requestFactory(timeouts).build();
        this.enabled = enabled;
    }

    public Route route(GeoPoint from, GeoPoint to) {
        if (enabled) {
            try {
                Route route = fromOsrm(from, to);
                if (route != null) {
                    return route;
                }
            } catch (RuntimeException e) {
                log.warn("OSRM routing failed, using straight-line estimate: {}", e.getMessage());
            }
        }
        return straightLine(from, to);
    }

    private Route fromOsrm(GeoPoint from, GeoPoint to) {
        String coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.lng(), from.lat(), to.lng(), to.lat());
        JsonNode body = osrm.get()
            .uri("/route/v1/driving/{coords}?overview=full&geometries=geojson", coords)
            .retrieve()
            .body(JsonNode.class);
        if (body == null || !"Ok".equals(body.path("code").asText()) || body.path("routes").isEmpty()) {
            return null;
        }
        JsonNode best = body.path("routes").get(0);
        List<GeoPoint> path = new ArrayList<>();
        for (JsonNode c : best.path("geometry").path("coordinates")) {
            path.add(new GeoPoint(c.get(1).asDouble(), c.get(0).asDouble())); // GeoJSON is [lng, lat]
        }
        return new Route(
            (int) Math.round(best.path("distance").asDouble()),
            (int) Math.round(best.path("duration").asDouble()),
            thin(path),
            "osrm");
    }

    static Route straightLine(GeoPoint from, GeoPoint to) {
        double meters = from.distanceMetersTo(to) * DETOUR_FACTOR;
        return new Route((int) Math.round(meters), (int) Math.round(meters / CITY_SPEED_MPS), List.of(from, to), "estimate");
    }

    /** Keep at most MAX_ROUTE_POINTS points so responses stay small. */
    private static List<GeoPoint> thin(List<GeoPoint> path) {
        if (path.size() <= MAX_ROUTE_POINTS) {
            return path;
        }
        int step = (int) Math.ceil(path.size() / (double) MAX_ROUTE_POINTS);
        List<GeoPoint> thinned = new ArrayList<>();
        for (int i = 0; i < path.size(); i += step) {
            thinned.add(path.get(i));
        }
        thinned.add(path.get(path.size() - 1));
        return thinned;
    }

    public record Route(int distanceMeters, int durationSeconds, List<GeoPoint> path, String source) {
    }
}
