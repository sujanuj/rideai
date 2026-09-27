package com.rideai.rides;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rideai.common.CurrentUser;
import com.rideai.rides.TripDtos.CancelRequest;
import com.rideai.rides.TripDtos.EstimateResponse;
import com.rideai.rides.TripDtos.TimelineEntry;
import com.rideai.rides.TripDtos.TripRequest;
import com.rideai.rides.TripDtos.TripResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService trips;

    public TripController(TripService trips) {
        this.trips = trips;
    }

    // ----- rider -----

    @PostMapping("/estimate")
    @PreAuthorize("hasRole('RIDER')")
    public EstimateResponse estimate(@Valid @RequestBody TripRequest req) {
        return trips.estimate(req);
    }

    @PostMapping
    @PreAuthorize("hasRole('RIDER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TripRequest req) {
        return trips.request(CurrentUser.id(jwt), req);
    }

    // ----- driver -----

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public TripResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return trips.accept(id, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/decline")
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        trips.decline(id, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/arrive")
    @PreAuthorize("hasRole('DRIVER')")
    public TripResponse arrive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return trips.arrive(id, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public TripResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return trips.start(id, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public TripResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return trips.complete(id, CurrentUser.id(jwt));
    }

    // ----- both -----

    @PostMapping("/{id}/cancel")
    public TripResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                               @Valid @RequestBody(required = false) CancelRequest req) {
        return trips.cancel(id, CurrentUser.id(jwt), req == null ? null : req.reason());
    }

    @GetMapping("/{id}")
    public TripResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return trips.get(id, CurrentUser.id(jwt));
    }

    @GetMapping("/{id}/timeline")
    public List<TimelineEntry> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return trips.timeline(id, CurrentUser.id(jwt));
    }

    /** 200 with the active trip, or 204 when there is none. */
    @GetMapping("/current")
    public ResponseEntity<TripResponse> current(@AuthenticationPrincipal Jwt jwt) {
        return trips.current(CurrentUser.id(jwt))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/me")
    public List<TripResponse> history(@AuthenticationPrincipal Jwt jwt) {
        return trips.history(CurrentUser.id(jwt));
    }
}
