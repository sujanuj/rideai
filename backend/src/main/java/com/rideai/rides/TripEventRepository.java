package com.rideai.rides;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {

    List<TripEvent> findByTripIdOrderByCreatedAtAscIdAsc(long tripId);

    default void record(long tripId, String type, Map<String, Object> payload) {
        save(new TripEvent(tripId, type, payload));
    }

    default void record(long tripId, String type) {
        record(tripId, type, Map.of());
    }
}
