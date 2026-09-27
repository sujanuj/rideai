package com.rideai.drivers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rideai.common.CurrentUser;
import com.rideai.common.GeoPoint;
import com.rideai.rides.TripDtos.TripResponse;
import com.rideai.rides.TripService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService drivers;
    private final DriverLocationService locations;
    private final TripService trips;

    public DriverController(DriverService drivers, DriverLocationService locations, TripService trips) {
        this.drivers = drivers;
        this.locations = locations;
        this.trips = trips;
    }

    public record StatusRequest(@NotNull DriverStatus status, @Valid GeoPoint position) {
    }

    public record DriverMeResponse(long id, String vehicle, String plate, BigDecimal rating,
                                   DriverStatus status, GeoPoint position) {
    }

    /** Anonymous car positions for the rider's map: no ids or names leave the server. */
    public record NearbyCar(double lat, double lng) {
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public DriverMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        long id = CurrentUser.id(jwt);
        Driver d = drivers.get(id);
        return new DriverMeResponse(id, d.getVehicle(), d.getPlate(), d.getRating(), d.getStatus(),
            locations.position(id).orElse(null));
    }

    @PutMapping("/me/status")
    @PreAuthorize("hasRole('DRIVER')")
    public DriverMeResponse setStatus(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StatusRequest req) {
        long id = CurrentUser.id(jwt);
        Driver d = drivers.setStatus(id, req.status(), req.position());
        return new DriverMeResponse(id, d.getVehicle(), d.getPlate(), d.getRating(), d.getStatus(),
            locations.position(id).orElse(null));
    }

    @PutMapping("/me/location")
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocation(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GeoPoint position) {
        drivers.updateLocation(CurrentUser.id(jwt), position);
    }

    /** 200 with the ride currently offered to this driver, or 204 if none. Phase 3 pushes this over WebSocket. */
    @GetMapping("/me/offer")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<TripResponse> offer(@AuthenticationPrincipal Jwt jwt) {
        return trips.currentOffer(CurrentUser.id(jwt))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/nearby")
    public List<NearbyCar> nearby(@RequestParam double lat, @RequestParam double lng) {
        return drivers.nearby(new GeoPoint(lat, lng)).stream()
            .map(n -> new NearbyCar(n.position().lat(), n.position().lng()))
            .toList();
    }
}
