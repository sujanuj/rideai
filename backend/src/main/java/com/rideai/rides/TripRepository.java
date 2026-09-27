package com.rideai.rides;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findFirstByRiderIdAndStatusIn(long riderId, Collection<TripStatus> statuses);

    Optional<Trip> findFirstByDriverIdAndStatusIn(long driverId, Collection<TripStatus> statuses);

    Optional<Trip> findFirstByOfferedDriverIdAndStatusAndOfferExpiresAtAfter(
        long driverId, TripStatus status, Instant now);

    boolean existsByOfferedDriverIdAndStatusAndOfferExpiresAtAfter(long driverId, TripStatus status, Instant now);

    @Query("select t.id from Trip t where t.status = com.rideai.rides.TripStatus.REQUESTED "
        + "and (t.offerExpiresAt is null or t.offerExpiresAt < :now)")
    List<Long> findRequestedWithoutLiveOffer(@Param("now") Instant now);

    @Query("select t from Trip t where t.riderId = :userId or t.driverId = :userId order by t.requestedAt desc")
    List<Trip> findHistory(@Param("userId") long userId, Pageable page);

    /**
     * Accept is a single conditional UPDATE, so two drivers (or two clicks) can never both win:
     * only the row that is still REQUESTED and still offered to this driver changes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Trip t set t.driverId = :driverId, t.status = com.rideai.rides.TripStatus.ACCEPTED, "
        + "t.acceptedAt = :now, t.offeredDriverId = null, t.offerExpiresAt = null "
        + "where t.id = :tripId and t.status = com.rideai.rides.TripStatus.REQUESTED "
        + "and t.offeredDriverId = :driverId and t.offerExpiresAt > :now")
    int acceptIfStillOffered(@Param("tripId") long tripId, @Param("driverId") long driverId, @Param("now") Instant now);
}
