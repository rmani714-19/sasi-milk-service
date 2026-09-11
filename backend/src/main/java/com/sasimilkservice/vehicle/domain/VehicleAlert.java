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
 * A condition worth an operator's attention. Only ever raised when telemetry
 * or the provider actually supports it — never invented. At most one OPEN
 * alert per (vehicle, type) at a time so a continuing condition (e.g. a
 * device still offline) doesn't spam new rows every sweep.
 */
@Entity
@Table(name = "vehicle_alerts")
@Getter
@Setter
public class VehicleAlert {

    public enum AlertType {
        OVERSPEED, DEVICE_OFFLINE, IGNITION_ON, IGNITION_OFF, HARSH_BRAKING,
        HARSH_ACCELERATION, GEOFENCE_ENTRY, GEOFENCE_EXIT, CAMERA_OFFLINE,
        DEVICE_TAMPER, ACCIDENT, OTHER
    }

    public enum Severity { INFO, WARNING, CRITICAL }

    public enum Status { OPEN, ACKNOWLEDGED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "driver_id")
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String message;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "speed_kmph")
    private BigDecimal speedKmph;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
