package com.rideai.realtime;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.rideai.rides.TripRepository;

/**
 * Security for WebSocket messages (HTTP security doesn't cover them):
 *  - CONNECT must carry a valid JWT, which becomes the session's user.
 *  - SUBSCRIBE to /topic/trips/{id} is only allowed for that trip's rider, driver or offered driver.
 *  - Any other /topic is refused; /user/queue/** is private to each user by design.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern TRIP_TOPIC = Pattern.compile("^/topic/trips/(\\d+)$");

    private final JwtDecoder jwtDecoder;
    private final TripRepository trips;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, TripRepository trips) {
        this.jwtDecoder = jwtDecoder;
        this.trips = trips;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        if (stomp.getCommand() == StompCommand.CONNECT) {
            stomp.setUser(authenticate(stomp.getFirstNativeHeader("Authorization")));
        } else if (stomp.getCommand() == StompCommand.SUBSCRIBE) {
            checkSubscription(stomp.getUser(), stomp.getDestination());
        } else if (stomp.getCommand() == StompCommand.SEND && stomp.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<GrantedAuthority> authorities = roles == null ? List.of()
                : roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
            return new JwtAuthenticationToken(jwt, authorities); // getName() = user id ("sub")
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid token");
        }
    }

    private void checkSubscription(Principal user, String destination) {
        if (user == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
        if (destination == null) {
            throw new MessageDeliveryException("No destination");
        }
        if (destination.startsWith("/user/")) {
            return;
        }
        Matcher m = TRIP_TOPIC.matcher(destination);
        if (!m.matches()) {
            throw new MessageDeliveryException("Unknown topic");
        }
        long tripId = Long.parseLong(m.group(1));
        long userId = Long.parseLong(user.getName());
        boolean allowed = trips.findById(tripId)
            .map(t -> t.getRiderId() == userId
                || Long.valueOf(userId).equals(t.getDriverId())
                || Long.valueOf(userId).equals(t.getOfferedDriverId()))
            .orElse(false);
        if (!allowed) {
            throw new MessageDeliveryException("Not your trip");
        }
    }
}
