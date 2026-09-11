package com.sasimilkservice.vehicle.api.dto;

import com.sasimilkservice.vehicle.domain.IgnitionStatus;
import com.sasimilkservice.vehicle.domain.MotionStatus;
import com.sasimilkservice.vehicle.domain.Vehicle;
import com.sasimilkservice.vehicle.domain.VehicleAlert;
import com.sasimilkservice.vehicle.domain.VehicleCamera;
import com.sasimilkservice.vehicle.domain.VehicleDevice;
import com.sasimilkservice.vehicle.domain.VehicleDeviceAssignment;
import com.sasimilkservice.vehicle.domain.VehicleDriverAssignment;
import com.sasimilkservice.vehicle.domain.VehicleLatestLocation;
import com.sasimilkservice.vehicle.domain.VehicleLocationHistory;
import com.sasimilkservice.vehicle.domain.VehicleTrip;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider.LiveStreamInfo;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider.PlaybackInfo;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider.RecordingSummary;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** All request/response payloads for the vehicle-tracking-and-camera module. */
public final class VehicleDtos {

    private VehicleDtos() {}

    // ---------------------------------------------------------------- vehicle

    public record CreateVehicleRequest(
            @NotBlank String code,
            @NotBlank String vehicleNumber,
            Vehicle.VehicleType vehicleType,
            String make,
            String model,
            Short modelYear,
            String description,
            @DecimalMin("0.0") BigDecimal capacityLitres,
            LocalDate registrationDate,
            LocalDate insuranceExpiry,
            LocalDate pollutionExpiry,
            String notes,
            Long driverId) {
    }

    public record UpdateVehicleRequest(
            Vehicle.VehicleType vehicleType,
            String make,
            String model,
            Short modelYear,
            String description,
            @DecimalMin("0.0") BigDecimal capacityLitres,
            LocalDate registrationDate,
            LocalDate insuranceExpiry,
            LocalDate pollutionExpiry,
            String notes) {
    }

    public record ChangeVehicleStatusRequest(@NotNull Vehicle.Status status) {
    }

    public record AssignDriverRequest(@Positive long driverId, LocalDate assignedFrom, String remarks) {
    }

    public record UnassignDriverRequest(LocalDate assignedTo) {
    }

    public record VehicleResponse(
            Long id, String code, String vehicleNumber, String vehicleType, String make, String model,
            Short modelYear, Long driverId, String description, BigDecimal capacityLitres,
            LocalDate registrationDate, LocalDate insuranceExpiry, LocalDate pollutionExpiry,
            String notes, String status) {

        public static VehicleResponse from(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getCode(), v.getRegistrationNumber(),
                    v.getVehicleType().name(), v.getMake(), v.getModel(), v.getModelYear(), v.getDriverId(),
                    v.getDescription(), v.getCapacityLitres(), v.getRegistrationDate(), v.getInsuranceExpiry(),
                    v.getPollutionExpiry(), v.getNotes(), v.getStatus().name());
        }
    }

    public record DriverAssignmentResponse(
            Long id, Long vehicleId, Long driverId, LocalDate assignedFrom, LocalDate assignedTo,
            Long assignedBy, String remarks, Instant createdAt) {

        public static DriverAssignmentResponse from(VehicleDriverAssignment a) {
            return new DriverAssignmentResponse(a.getId(), a.getVehicleId(), a.getDriverId(), a.getAssignedFrom(),
                    a.getAssignedTo(), a.getAssignedBy(), a.getRemarks(), a.getCreatedAt());
        }
    }

    // ----------------------------------------------------------------- device

    public record RegisterDeviceRequest(
            @NotNull VehicleDevice.DeviceType deviceType,
            String provider,
            @NotBlank String externalDeviceId,
            String deviceModel,
            String imei,
            String serialNumber,
            /** Raw SIM number, write-only — only its masked form is ever persisted. */
            String simNumber,
            String firmwareVersion) {
    }

    public record AttachDeviceRequest(@Positive long deviceId, LocalDate installationDate,
                                      boolean primary, String remarks) {
    }

    public record DetachDeviceRequest(LocalDate removalDate, String remarks) {
    }

    public record DeviceResponse(
            Long id, Long vehicleId, String deviceType, String provider, String externalDeviceId,
            String deviceModel, String imei, String serialNumber, String simNumberMasked, String firmwareVersion,
            LocalDate installationDate, LocalDate removedDate, boolean primary, Instant lastSeenAt, String status) {

        public static DeviceResponse from(VehicleDevice d) {
            return new DeviceResponse(d.getId(), d.getVehicleId(), d.getDeviceType().name(), d.getProvider(),
                    d.getExternalDeviceId(), d.getDeviceModel(), d.getImei(), d.getSerialNumber(),
                    d.getSimNumberMasked(), d.getFirmwareVersion(), d.getInstallationDate(), d.getRemovedDate(),
                    d.isPrimary(), d.getLastSeenAt(), d.getStatus().name());
        }
    }

    public record DeviceAssignmentResponse(
            Long id, Long vehicleId, Long deviceId, LocalDate assignedFrom, LocalDate assignedTo,
            Long assignedBy, Long removedBy, String remarks, Instant createdAt) {

        public static DeviceAssignmentResponse from(VehicleDeviceAssignment a) {
            return new DeviceAssignmentResponse(a.getId(), a.getVehicleId(), a.getDeviceId(), a.getAssignedFrom(),
                    a.getAssignedTo(), a.getAssignedBy(), a.getRemovedBy(), a.getRemarks(), a.getCreatedAt());
        }
    }

    // --------------------------------------------------------------- location

    /** A manual/testing ingest — the same shape a real provider adapter would call internally. */
    public record IngestLocationRequest(
            Long deviceId,
            Instant providerTimestamp,
            @NotNull BigDecimal latitude,
            @NotNull BigDecimal longitude,
            BigDecimal speedKmph,
            BigDecimal directionDegrees,
            BigDecimal altitude,
            IgnitionStatus ignitionStatus,
            MotionStatus motionStatus,
            BigDecimal gpsAccuracy) {
    }

    public record LocationResponse(
            Long vehicleId, Long deviceId, BigDecimal latitude, BigDecimal longitude, BigDecimal speedKmph,
            BigDecimal directionDegrees, BigDecimal altitude, String ignitionStatus, String motionStatus,
            BigDecimal gpsAccuracy, Instant providerTimestamp, Instant receivedAt) {

        public static LocationResponse from(VehicleLatestLocation l) {
            return new LocationResponse(l.getVehicleId(), l.getDeviceId(), l.getLatitude(), l.getLongitude(),
                    l.getSpeedKmph(), l.getDirectionDegrees(), l.getAltitude(),
                    l.getIgnitionStatus() == null ? null : l.getIgnitionStatus().name(),
                    l.getMotionStatus() == null ? null : l.getMotionStatus().name(),
                    l.getGpsAccuracy(), l.getProviderTimestamp(), l.getReceivedAt());
        }

        public static LocationResponse from(VehicleLocationHistory h) {
            return new LocationResponse(h.getVehicleId(), h.getDeviceId(), h.getLatitude(), h.getLongitude(),
                    h.getSpeedKmph(), h.getDirectionDegrees(), h.getAltitude(),
                    h.getIgnitionStatus() == null ? null : h.getIgnitionStatus().name(),
                    h.getMotionStatus() == null ? null : h.getMotionStatus().name(),
                    h.getGpsAccuracy(), h.getProviderTimestamp(), h.getReceivedAt());
        }
    }

    // ------------------------------------------------------------------ trip

    public record TripResponse(
            Long id, Long vehicleId, Long deviceId, Long driverId, Instant startedAt, Instant endedAt,
            BigDecimal startLatitude, BigDecimal startLongitude, BigDecimal endLatitude, BigDecimal endLongitude,
            BigDecimal distanceKm, Long durationSeconds, BigDecimal maxSpeedKmph, BigDecimal averageSpeedKmph,
            String status) {

        public static TripResponse from(VehicleTrip t) {
            return new TripResponse(t.getId(), t.getVehicleId(), t.getDeviceId(), t.getDriverId(), t.getStartedAt(),
                    t.getEndedAt(), t.getStartLatitude(), t.getStartLongitude(), t.getEndLatitude(),
                    t.getEndLongitude(), t.getDistanceKm(), t.getDurationSeconds(), t.getMaxSpeedKmph(),
                    t.getAverageSpeedKmph(), t.getStatus().name());
        }
    }

    // --------------------------------------------------------------- camera

    public record RegisterCameraRequest(
            @Positive long deviceId, @NotNull VehicleCamera.CameraType cameraType,
            Short cameraChannel, String cameraName, String providerCameraId) {
    }

    public record CameraResponse(
            Long id, Long vehicleId, Long deviceId, String cameraType, Short cameraChannel, String cameraName,
            String status) {

        public static CameraResponse from(VehicleCamera c) {
            return new CameraResponse(c.getId(), c.getVehicleId(), c.getDeviceId(), c.getCameraType().name(),
                    c.getCameraChannel(), c.getCameraName(), c.getStatus().name());
        }
    }

    /** {@code available=false} means "camera offline / not connected" — never a fabricated URL. */
    public record LiveStreamResponse(boolean available, String protocol, String url, Instant expiresAt) {
        public static LiveStreamResponse unavailable() {
            return new LiveStreamResponse(false, null, null, null);
        }

        public static LiveStreamResponse from(LiveStreamInfo s) {
            return new LiveStreamResponse(true, s.protocol(), s.url(), s.expiresAt());
        }
    }

    public record PlaybackResponse(boolean available, String protocol, String url, Instant expiresAt) {
        public static PlaybackResponse unavailable() {
            return new PlaybackResponse(false, null, null, null);
        }

        public static PlaybackResponse from(PlaybackInfo p) {
            return new PlaybackResponse(true, p.protocol(), p.url(), p.expiresAt());
        }
    }

    public record RecordingResponse(String recordingId, Instant startedAt, Instant endedAt, String protocol) {
        public static RecordingResponse from(RecordingSummary r) {
            return new RecordingResponse(r.recordingId(), r.startedAt(), r.endedAt(), r.protocol());
        }
    }

    // ------------------------------------------------------------------ alert

    public record AlertResponse(
            Long id, Long vehicleId, Long deviceId, Long driverId, String alertType, String severity,
            String message, BigDecimal latitude, BigDecimal longitude, BigDecimal speedKmph, Instant occurredAt,
            Long acknowledgedBy, Instant acknowledgedAt, String status) {

        public static AlertResponse from(VehicleAlert a) {
            return new AlertResponse(a.getId(), a.getVehicleId(), a.getDeviceId(), a.getDriverId(),
                    a.getAlertType().name(), a.getSeverity().name(), a.getMessage(), a.getLatitude(),
                    a.getLongitude(), a.getSpeedKmph(), a.getOccurredAt(), a.getAcknowledgedBy(),
                    a.getAcknowledgedAt(), a.getStatus().name());
        }
    }
}
