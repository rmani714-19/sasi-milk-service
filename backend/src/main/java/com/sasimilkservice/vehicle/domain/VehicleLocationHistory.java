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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Every GPS fix ever received, kept forever (retention/archival is a future
 * policy layered on top — nothing here assumes the table stays small).
 * Append-only: a fix is inserted once and never updated or deleted.
 */
@Entity
@Table(name = "vehicle_location_history")
@Getter
@Setter
public class VehicleLocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "provider_timestamp", nullable = false)
    private Instant providerTimestamp;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> raw;

    @Column(name = "received_at", nullable = false, updatable = false, insertable = false)
    private Instant receivedAt;
}
