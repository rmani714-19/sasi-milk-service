package com.sasimilkservice.vehicle.api;

import com.sasimilkservice.vehicle.domain.Vehicle;
import com.sasimilkservice.vehicle.domain.VehicleDevice;
import com.sasimilkservice.vehicle.domain.VehicleLocation;
import com.sasimilkservice.vehicle.service.VehicleService;
import com.sasimilkservice.vehicle.spi.VehicleStatusQuery.VehicleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ALL','VIEW_DASHBOARD')")
    public List<VehicleResponse> list() {
        return service.findAll().stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('ALL','VIEW_DASHBOARD')")
    public List<VehicleStatus> status() {
        return service.allStatuses();
    }

    @GetMapping("/{id}/locations")
    @PreAuthorize("hasAnyAuthority('ALL','VIEW_DASHBOARD')")
    public List<LocationResponse> locations(@PathVariable long id,
                                            @RequestParam(defaultValue = "50") int limit) {
        return service.locationHistory(id, limit).stream().map(LocationResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ALL')")
    public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest r) {
        return VehicleResponse.from(service.create(r.code(), r.registrationNumber(),
                r.description(), r.capacityLitres()));
    }

    @PostMapping("/{id}/devices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ALL')")
    public DeviceResponse registerDevice(@PathVariable long id, @Valid @RequestBody RegisterDeviceRequest r) {
        return DeviceResponse.from(service.registerDevice(id, r.deviceType(), r.provider(), r.externalDeviceId()));
    }

    /** Ingest a GPS fix (provider webhook or manual). */
    @PostMapping("/{id}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ALL')")
    public LocationResponse ingest(@PathVariable long id, @Valid @RequestBody IngestLocationRequest r) {
        return LocationResponse.from(service.recordLocation(id, r.recordedAt(), r.latitude(),
                r.longitude(), r.speedKmph(), r.headingDeg(), r.ignition()));
    }

    public record CreateVehicleRequest(@NotBlank String code, @NotBlank String registrationNumber,
                                       String description, BigDecimal capacityLitres) {
    }

    public record RegisterDeviceRequest(@NotNull VehicleDevice.DeviceType deviceType,
                                        String provider, @NotBlank String externalDeviceId) {
    }

    public record IngestLocationRequest(Instant recordedAt, @NotNull BigDecimal latitude,
                                        @NotNull BigDecimal longitude, BigDecimal speedKmph,
                                        BigDecimal headingDeg, Boolean ignition) {
    }

    public record VehicleResponse(Long id, String code, String registrationNumber, String description,
                                  BigDecimal capacityLitres, String status) {
        static VehicleResponse from(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getCode(), v.getRegistrationNumber(),
                    v.getDescription(), v.getCapacityLitres(), v.getStatus().name());
        }
    }

    public record DeviceResponse(Long id, Long vehicleId, String deviceType, String provider,
                                 String externalDeviceId, String status) {
        static DeviceResponse from(VehicleDevice d) {
            return new DeviceResponse(d.getId(), d.getVehicleId(), d.getDeviceType().name(),
                    d.getProvider(), d.getExternalDeviceId(), d.getStatus().name());
        }
    }

    public record LocationResponse(Long id, Long vehicleId, Instant recordedAt, BigDecimal latitude,
                                   BigDecimal longitude, BigDecimal speedKmph, Boolean ignition) {
        static LocationResponse from(VehicleLocation l) {
            return new LocationResponse(l.getId(), l.getVehicleId(), l.getRecordedAt(), l.getLatitude(),
                    l.getLongitude(), l.getSpeedKmph(), l.getIgnition());
        }
    }
}
