package com.rideai.rides;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {

    List<TripEvent> findByTripIdOrderByCreatedAtAscIdAsc(long tripId);

    long countByTripIdAndType(long tripId, String type);
}
