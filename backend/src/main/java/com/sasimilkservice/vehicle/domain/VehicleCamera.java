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

/**
 * One camera channel on a device. Capability (does this device even have a
 * cabin camera?) comes from what's actually registered here — never assumed.
 */
@Entity
@Table(name = "vehicle_cameras")
@Getter
@Setter
public class VehicleCamera {

    public enum CameraType { FRONT, CABIN, REAR, SIDE, OTHER }

    public enum Status { ACTIVE, INACTIVE, FAULTY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "camera_type", nullable = false)
    private CameraType cameraType;

    @Column(name = "camera_channel")
    private Short cameraChannel;

    @Column(name = "camera_name")
    private String cameraName;

    /** The provider's own identifier for this camera/channel — opaque to us. */
    @Column(name = "provider_camera_id")
    private String providerCameraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
}
