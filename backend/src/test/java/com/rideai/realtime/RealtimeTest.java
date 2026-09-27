package com.rideai.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.rideai.IntegrationTest;

/** The rider's app gets trip updates and driver positions pushed over STOMP. */
class RealtimeTest extends IntegrationTest {

    private static final Map<String, Object> TRIP = Map.of(
        "pickup", Map.of("lat", 33.4242, "lng", -111.9281),
        "dropoff", Map.of("lat", 33.4152, "lng", -111.8315));

    @LocalServerPort
    int port;

    private StompSession connect(Account who) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders headers = new StompHeaders();
        if (who != null) {
            headers.add("Authorization", "Bearer " + who.token());
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
            new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        return received;
    }

    @Test
    void riderSeesAcceptAndDriverPositionLive() throws Exception {
        Account rider = registerRider();
        Account driver = registerDriver();
        goOnline(driver, 33.4250, -111.9290);
        long tripId = read(postAs(rider, "/api/trips", TRIP).andReturn()).path("id").asLong();
        awaitOffer(driver, tripId);

        StompSession session = connect(rider);
        BlockingQueue<Map<String, Object>> updates = subscribe(session, "/topic/trips/" + tripId);
        Thread.sleep(500); // let the subscription register

        postAs(driver, "/api/trips/" + tripId + "/accept", null).andExpect(status().isOk());
        Map<String, Object> accepted = nextOfType(updates, "trip");
        assertThat(accepted.get("event")).isEqualTo("DRIVER_ASSIGNED");

        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/drivers/me/location"),
            driver, Map.of("lat", 33.4246, "lng", -111.9285)).andExpect(status().isNoContent());
        Map<String, Object> location = nextOfType(updates, "location");
        assertThat(((Number) location.get("lat")).doubleValue()).isEqualTo(33.4246);

        session.disconnect();
    }

    @Test
    void connectionsNeedAValidToken() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect(new Account(1, "not-a-jwt"))).isInstanceOf(Exception.class);
    }

    @Test
    void strangersGetNothingFromSomeoneElsesTrip() throws Exception {
        Account rider = registerRider();
        Account stranger = registerRider();
        Account driver = registerDriver();
        goOnline(driver, 33.4250, -111.9290);
        long tripId = read(postAs(rider, "/api/trips", TRIP).andReturn()).path("id").asLong();
        awaitOffer(driver, tripId);

        StompSession session = connect(stranger);
        BlockingQueue<Map<String, Object>> updates = subscribe(session, "/topic/trips/" + tripId);
        Thread.sleep(500);
        postAs(driver, "/api/trips/" + tripId + "/accept", null).andExpect(status().isOk());

        assertThat(updates.poll(2, TimeUnit.SECONDS)).isNull();
    }

    private static Map<String, Object> nextOfType(BlockingQueue<Map<String, Object>> queue, String type)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> msg = queue.poll(500, TimeUnit.MILLISECONDS);
            if (msg != null && type.equals(msg.get("type"))) {
                return msg;
            }
        }
        throw new AssertionError("No '" + type + "' message within 10 s");
    }
}
