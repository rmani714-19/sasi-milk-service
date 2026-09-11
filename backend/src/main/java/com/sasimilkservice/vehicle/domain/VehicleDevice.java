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

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * A GPS/camera hardware unit. {@code vehicleId} is the device's <em>current</em>
 * attachment (null when unassigned/in stock) — the full attach/detach history
 * lives in {@link VehicleDeviceAssignment}. Never carries the device's real
 * API credentials; those stay in server-side provider configuration.
 */
@Entity
@Table(name = "vehicle_devices")
@Getter
@Setter
public class VehicleDevice {

    public enum DeviceType { GPS, GPS_CAMERA, DASHCAM, MDVR, OTHER }

    public enum Status { ACTIVE, INACTIVE, REMOVED, FAULTY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Current vehicle, or null when this device is detached / in stock. */
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    private DeviceType deviceType;

    /** Free-text vendor/provider key (e.g. "MOCK") — never a credential. */
    private String provider;

    @Column(name = "external_device_id", nullable = false)
    private String externalDeviceId;

    @Column(name = "device_model")
    private String deviceModel;

    private String imei;

    @Column(name = "serial_number")
    private String serialNumber;

    /** Only ever holds a masked value (e.g. "******4321") — the raw SIM number is never persisted. */
    @Column(name = "sim_number_masked")
    private String simNumberMasked;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Column(name = "removed_date")
    private LocalDate removedDate;

    /** The primary tracking device on its vehicle — the one the dashboard reads by default. */
    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config = new HashMap<>();

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom = Instant.now();

    @Column(name = "active_to")
    private Instant activeTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
}
