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
 * One row per vehicle, upserted on every fix — the fast path for the
 * dashboard and live map. {@link VehicleLocationHistory} is the append-only
 * record of every fix ever received; this table never grows past one row per
 * vehicle and is never read for history.
 */
@Entity
@Table(name = "vehicle_latest_locations")
@Getter
@Setter
public class VehicleLatestLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false, unique = true)
    private Long vehicleId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(nullable = false)
    private BigDecimal latitude;

    @Column(nullable = false)
    private BigDecimal longitude;

    @Column(name = "speed_kmph")
    private BigDecimal speedKmph;

    @Column(name = "direction_degrees")
    private BigDecimal directionDegrees;

    private BigDecimal altitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "ignition_status")
    private IgnitionStatus ignitionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "motion_status")
    private MotionStatus motionStatus;

    @Column(name = "gps_accuracy")
    private BigDecimal gpsAccuracy;

    /** The device/provider's own timestamp for this fix — not when we received it. */
    @Column(name = "provider_timestamp", nullable = false)
    private Instant providerTimestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();
}
