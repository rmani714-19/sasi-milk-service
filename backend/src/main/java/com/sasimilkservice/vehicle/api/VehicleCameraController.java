package com.sasimilkservice.vehicle.api;

import com.sasimilkservice.vehicle.api.dto.VehicleDtos.CameraResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.LiveStreamResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.PlaybackResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.RecordingResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.RegisterCameraRequest;
import com.sasimilkservice.vehicle.service.VehicleCameraService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Camera master data and a provider-independent view onto live/playback/
 * recordings. {@code available=false} on the stream/playback responses means
 * "not connected right now" — never a fabricated URL.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleCameraController {

    private static final String VIEW_CAMERA = "hasAnyAuthority('VIEW_CAMERA','ALL')";
    private static final String MANAGE = "hasAnyAuthority('MANAGE_VEHICLES','ALL')";

    private final VehicleCameraService service;

    public VehicleCameraController(VehicleCameraService service) {
        this.service = service;
    }

    @GetMapping("/{vehicleId}/cameras")
    @PreAuthorize(VIEW_CAMERA)
    public List<CameraResponse> cameras(@PathVariable long vehicleId) {
        return service.camerasOf(vehicleId).stream().map(CameraResponse::from).toList();
    }

    @PostMapping("/{vehicleId}/cameras")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public CameraResponse register(@PathVariable long vehicleId, @Valid @RequestBody RegisterCameraRequest r) {
        return CameraResponse.from(service.register(vehicleId, r.deviceId(), r.cameraType(), r.cameraChannel(),
                r.cameraName(), r.providerCameraId()));
    }

    @GetMapping("/{vehicleId}/cameras/{cameraId}/live")
    @PreAuthorize(VIEW_CAMERA)
    public LiveStreamResponse live(@PathVariable long vehicleId, @PathVariable long cameraId) {
        return service.liveStream(vehicleId, cameraId).map(LiveStreamResponse::from)
                .orElseGet(LiveStreamResponse::unavailable);
    }

    @GetMapping("/{vehicleId}/cameras/{cameraId}/playback")
    @PreAuthorize(VIEW_CAMERA)
    public PlaybackResponse playback(
            @PathVariable long vehicleId, @PathVariable long cameraId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.playback(vehicleId, cameraId, from, to).map(PlaybackResponse::from)
                .orElseGet(PlaybackResponse::unavailable);
    }

    @GetMapping("/{vehicleId}/cameras/{cameraId}/recordings")
    @PreAuthorize(VIEW_CAMERA)
    public List<RecordingResponse> recordings(
            @PathVariable long vehicleId, @PathVariable long cameraId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.recordings(vehicleId, cameraId, from, to).stream().map(RecordingResponse::from).toList();
    }
}
