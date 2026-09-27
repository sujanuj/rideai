package com.rideai.drivers;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Driver-only details. Shares its primary key with the users row (one-to-one). */
@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String vehicle;

    @Column(nullable = false)
    private String plate;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating = new BigDecimal("5.0");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status = DriverStatus.OFFLINE;

    protected Driver() {
        // for JPA
    }

    public Driver(Long userId, String vehicle, String plate) {
        this.userId = userId;
        this.vehicle = vehicle;
        this.plate = plate;
    }

    public Long getUserId() {
        return userId;
    }

    public String getVehicle() {
        return vehicle;
    }

    public String getPlate() {
        return plate;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }
}
