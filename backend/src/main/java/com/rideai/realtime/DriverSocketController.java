package com.rideai.realtime;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.rideai.common.GeoPoint;
import com.rideai.drivers.DriverService;

/** Drivers stream their GPS position over the socket: SEND /app/driver/location {lat, lng}. */
@Controller
public class DriverSocketController {

    private final DriverService drivers;

    public DriverSocketController(DriverService drivers) {
        this.drivers = drivers;
    }

    @MessageMapping("/driver/location")
    public void location(GeoPoint position, Principal principal) {
        if (!(principal instanceof Authentication auth)
            || auth.getAuthorities().stream().noneMatch(a -> "ROLE_DRIVER".equals(a.getAuthority()))) {
            return; // only drivers send locations
        }
        if (position == null || position.lat() == null || position.lng() == null
            || Math.abs(position.lat()) > 90 || Math.abs(position.lng()) > 180) {
            return;
        }
        try {
            drivers.updateLocation(Long.parseLong(principal.getName()), position);
        } catch (RuntimeException ignored) {
            // e.g. driver is offline; the app will re-sync over REST
        }
    }
}
