package com.rideai.rides;

import java.time.Instant;

import com.rideai.common.ApiException;
import com.rideai.common.GeoPoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "driver_id")
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.REQUESTED;

    @Column(name = "pickup_lat", nullable = false)
    private Double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private Double pickupLng;

    @Column(name = "dropoff_lat", nullable = false)
    private Double dropoffLat;

    @Column(name = "dropoff_lng", nullable = false)
    private Double dropoffLng;

    @Column(name = "est_distance_m")
    private Integer estDistanceM;

    @Column(name = "est_duration_s")
    private Integer estDurationS;

    @Column(name = "fare_cents")
    private Integer fareCents;

    @Column(name = "offered_driver_id")
    private Long offeredDriverId;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "pickup_eta_s")
    private Integer pickupEtaS;

    @Column(name = "actual_distance_m")
    private Integer actualDistanceM;

    protected Trip() {
        // for JPA
    }

    public Trip(long riderId, GeoPoint pickup, GeoPoint dropoff, int distanceM, int durationS, int fareCents) {
        this.riderId = riderId;
        this.pickupLat = pickup.lat();
        this.pickupLng = pickup.lng();
        this.dropoffLat = dropoff.lat();
        this.dropoffLng = dropoff.lng();
        this.estDistanceM = distanceM;
        this.estDurationS = durationS;
        this.fareCents = fareCents;
    }

    /** The only way to change status: illegal moves throw 409 INVALID_TRANSITION. */
    public void moveTo(TripStatus next) {
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("INVALID_TRANSITION",
                "Trip " + id + " can't go from " + status + " to " + next);
        }
        Instant now = Instant.now();
        switch (next) {
            case ACCEPTED -> acceptedAt = now;
            case ARRIVED -> arrivedAt = now;
            case IN_PROGRESS -> startedAt = now;
            case COMPLETED -> completedAt = now;
            case CANCELLED -> cancelledAt = now;
            default -> { }
        }
        if (next != TripStatus.REQUESTED) {
            clearOffer();
        }
        status = next;
    }

    public void assignDriver(long driverId) {
        this.driverId = driverId;
    }

    public void offerTo(long driverId, Instant expiresAt) {
        this.offeredDriverId = driverId;
        this.offerExpiresAt = expiresAt;
    }

    public void clearOffer() {
        this.offeredDriverId = null;
        this.offerExpiresAt = null;
    }

    public boolean hasLiveOffer(Instant now) {
        return offeredDriverId != null && offerExpiresAt != null && offerExpiresAt.isAfter(now);
    }

    public void setPickupEtaS(Integer pickupEtaS) {
        this.pickupEtaS = pickupEtaS;
    }

    public void setActualDistanceM(Integer actualDistanceM) {
        this.actualDistanceM = actualDistanceM;
    }

    public Integer getPickupEtaS() {
        return pickupEtaS;
    }

    public Integer getActualDistanceM() {
        return actualDistanceM;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public GeoPoint pickup() {
        return new GeoPoint(pickupLat, pickupLng);
    }

    public GeoPoint dropoff() {
        return new GeoPoint(dropoffLat, dropoffLng);
    }

    public Long getId() {
        return id;
    }

    public Long getRiderId() {
        return riderId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public TripStatus getStatus() {
        return status;
    }

    public Integer getEstDistanceM() {
        return estDistanceM;
    }

    public Integer getEstDurationS() {
        return estDurationS;
    }

    public Integer getFareCents() {
        return fareCents;
    }

    public Long getOfferedDriverId() {
        return offeredDriverId;
    }

    public Instant getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
