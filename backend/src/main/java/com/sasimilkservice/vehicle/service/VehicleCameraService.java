package com.sasimilkservice.vehicle.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.vehicle.domain.VehicleCamera;
import com.sasimilkservice.vehicle.domain.VehicleDevice;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider.CameraStatus;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider.LiveStreamInfo;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider.PlaybackInfo;
import com.sasimilkservice.vehicle.provider.VehicleCameraProvider.RecordingSummary;
import com.sasimilkservice.vehicle.repository.VehicleCameraRepository;
import com.sasimilkservice.vehicle.repository.VehicleDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Camera master data plus a thin, audited pass-through to
 * {@link VehicleCameraProvider}. Every live-view request is audited here so
 * it is recorded regardless of which controller/entry point calls it.
 */
@Service
@Transactional(readOnly = true)
public class VehicleCameraService {

    private final VehicleCameraRepository cameras;
    private final VehicleDeviceRepository devices;
    private final VehicleCameraProvider provider;
    private final AuditRecorder audit;

    public VehicleCameraService(VehicleCameraRepository cameras, VehicleDeviceRepository devices,
                                VehicleCameraProvider provider, AuditRecorder audit) {
        this.cameras = cameras;
        this.devices = devices;
        this.provider = provider;
        this.audit = audit;
    }

    public List<VehicleCamera> camerasOf(long vehicleId) {
        return cameras.findByVehicleId(vehicleId);
    }

    public VehicleCamera require(long id) {
        return cameras.findById(id).orElseThrow(() -> NotFoundException.of("VehicleCamera", id));
    }

    @Transactional
    public VehicleCamera register(long vehicleId, long deviceId, VehicleCamera.CameraType cameraType,
                                  Short channel, String name, String providerCameraId) {
        VehicleDevice d = devices.findById(deviceId).orElseThrow(() -> NotFoundException.of("VehicleDevice", deviceId));
        if (!Objects.equals(d.getVehicleId(), vehicleId)) {
            throw new BusinessRuleException("This device is not attached to this vehicle");
        }
        VehicleCamera c = new VehicleCamera();
        c.setVehicleId(vehicleId);
        c.setDeviceId(deviceId);
        c.setCameraType(cameraType);
        c.setCameraChannel(channel);
        c.setCameraName(name);
        c.setProviderCameraId(providerCameraId);
        c = cameras.save(c);
        audit.record("VEHICLE_CAMERA_REGISTERED", "Vehicle", vehicleId,
                cameraType + " camera registered (" + c.getId() + ")");
        return c;
    }

    public CameraStatus statusOf(long vehicleId, long cameraId) {
        return provider.getCameraStatus(providerId(requireOnVehicle(vehicleId, cameraId)));
    }

    /** Every call is audited — this is the only path the live-stream reference can leave through. */
    @Transactional
    public Optional<LiveStreamInfo> liveStream(long vehicleId, long cameraId) {
        VehicleCamera c = requireOnVehicle(vehicleId, cameraId);
        Optional<LiveStreamInfo> stream = provider.getLiveStream(providerId(c));
        audit.record("VEHICLE_CAMERA_LIVE_VIEWED", "VehicleCamera", cameraId,
                "Live view requested for vehicle " + vehicleId + " (" + c.getCameraType() + ")");
        return stream;
    }

    public List<RecordingSummary> recordings(long vehicleId, long cameraId, Instant from, Instant to) {
        VehicleCamera c = requireOnVehicle(vehicleId, cameraId);
        return provider.getRecordingList(providerId(c), from, to);
    }

    @Transactional
    public Optional<PlaybackInfo> playback(long vehicleId, long cameraId, Instant from, Instant to) {
        VehicleCamera c = requireOnVehicle(vehicleId, cameraId);
        Optional<PlaybackInfo> pb = provider.getPlayback(providerId(c), from, to);
        audit.record("VEHICLE_CAMERA_PLAYBACK_VIEWED", "VehicleCamera", cameraId,
                "Playback requested for vehicle " + vehicleId + " (" + from + " to " + to + ")");
        return pb;
    }

    private VehicleCamera requireOnVehicle(long vehicleId, long cameraId) {
        VehicleCamera c = require(cameraId);
        if (!Objects.equals(c.getVehicleId(), vehicleId)) {
            throw NotFoundException.of("VehicleCamera", cameraId);
        }
        return c;
    }

    private static String providerId(VehicleCamera c) {
        return c.getProviderCameraId() != null ? c.getProviderCameraId() : String.valueOf(c.getId());
    }
}
