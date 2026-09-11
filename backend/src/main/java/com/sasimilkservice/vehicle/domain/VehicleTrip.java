package com.sasimilkservice.vehicle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A trip derived from telemetry — never fabricated when the device is
 * offline. Detection thresholds live in {@code VehicleTrackingProperties}.
 */
@Entity
@Table(name = "vehicle_trips")
@Getter
@Setter
public class VehicleTrip {

    public enum Status { ONGOING, COMPLETED, UNKNOWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "device_id")
    private Long deviceId;

    /** The driver assigned to the vehicle when the trip started (snapshot, not live). */
    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "start_latitude")
    private BigDecimal startLatitude;
    @Column(name = "start_longitude")
    private BigDecimal startLongitude;
    @Column(name = "end_latitude")
    private BigDecimal endLatitude;
    @Column(name = "end_longitude")
    private BigDecimal endLongitude;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "max_speed_kmph")
    private BigDecimal maxSpeedKmph;

    @Column(name = "average_speed_kmph")
    private BigDecimal averageSpeedKmph;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ONGOING;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
