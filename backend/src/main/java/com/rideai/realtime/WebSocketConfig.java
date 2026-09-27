package com.rideai.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP (a small pub/sub protocol on top of WebSocket).
 *
 *   Connect:     ws://localhost:8080/ws   with header  Authorization: Bearer <jwt>
 *   Subscribe:   /topic/trips/{id}        trip status changes + live driver position (rider and driver)
 *                /user/queue/offers       ride offers for the logged-in driver
 *   Send:        /app/driver/location     {lat, lng} from a driver's phone
 *
 * The broker is Spring's in-memory one, fine for a single backend instance. With several
 * instances you'd switch to a broker relay (RabbitMQ) or fan out via Redis pub/sub.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor auth;
    private final String[] allowedOrigins;

    public WebSocketConfig(StompAuthInterceptor auth,
                           @Value("${rideai.websocket.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
        this.auth = auth;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
