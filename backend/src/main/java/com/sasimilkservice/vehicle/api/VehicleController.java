package com.sasimilkservice.vehicle.api;

import com.sasimilkservice.vehicle.api.dto.VehicleDtos.AssignDriverRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.AttachDeviceRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.ChangeVehicleStatusRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.CreateVehicleRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.DetachDeviceRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.DeviceAssignmentResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.DeviceResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.DriverAssignmentResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.RegisterDeviceRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.UnassignDriverRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.UpdateVehicleRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.VehicleResponse;
import com.sasimilkservice.vehicle.domain.Vehicle;
import com.sasimilkservice.vehicle.service.VehicleDeviceService;
import com.sasimilkservice.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private static final String VIEW = "hasAnyAuthority('VIEW_VEHICLES','VIEW_DASHBOARD','ALL')";
    private static final String MANAGE = "hasAnyAuthority('MANAGE_VEHICLES','ALL')";
    private static final String MANAGE_DEVICES = "hasAnyAuthority('MANAGE_DEVICE_ASSIGNMENT','MANAGE_VEHICLES','ALL')";

    private final VehicleService service;
    private final VehicleDeviceService deviceService;

    public VehicleController(VehicleService service, VehicleDeviceService deviceService) {
        this.service = service;
        this.deviceService = deviceService;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public List<VehicleResponse> list() {
        return service.findAll().stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/{vehicleId}")
    @PreAuthorize(VIEW)
    public VehicleResponse get(@PathVariable long vehicleId) {
        return VehicleResponse.from(service.require(vehicleId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest r) {
        Vehicle v = service.create(r.code(), r.vehicleNumber(), r.vehicleType(), r.make(), r.model(), r.modelYear(),
                r.description(), r.capacityLitres(), r.registrationDate(), r.insuranceExpiry(), r.pollutionExpiry(),
                r.notes(), r.driverId());
        return VehicleResponse.from(v);
    }

    @PutMapping("/{vehicleId}")
    @PreAuthorize(MANAGE)
    public VehicleResponse update(@PathVariable long vehicleId, @Valid @RequestBody UpdateVehicleRequest r) {
        Vehicle v = service.update(vehicleId, r.vehicleType(), r.make(), r.model(), r.modelYear(), r.description(),
                r.capacityLitres(), r.registrationDate(), r.insuranceExpiry(), r.pollutionExpiry(), r.notes());
        return VehicleResponse.from(v);
    }

    @PatchMapping("/{vehicleId}/status")
    @PreAuthorize(MANAGE)
    public VehicleResponse changeStatus(@PathVariable long vehicleId, @Valid @RequestBody ChangeVehicleStatusRequest r) {
        return VehicleResponse.from(service.changeStatus(vehicleId, r.status()));
    }

    // ---- driver assignment ----

    @GetMapping("/{vehicleId}/driver/history")
    @PreAuthorize(VIEW)
    public List<DriverAssignmentResponse> driverHistory(@PathVariable long vehicleId) {
        return service.driverAssignmentHistory(vehicleId).stream().map(DriverAssignmentResponse::from).toList();
    }

    @PostMapping("/{vehicleId}/driver/assign")
    @PreAuthorize(MANAGE)
    public DriverAssignmentResponse assignDriver(@PathVariable long vehicleId, @Valid @RequestBody AssignDriverRequest r) {
        return DriverAssignmentResponse.from(service.assignDriver(vehicleId, r.driverId(), r.assignedFrom(), r.remarks()));
    }

    @PostMapping("/{vehicleId}/driver/unassign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    public void unassignDriver(@PathVariable long vehicleId, @RequestBody(required = false) UnassignDriverRequest r) {
        service.unassignDriver(vehicleId, r == null ? null : r.assignedTo());
    }

    // ---- devices ----

    @GetMapping("/devices")
    @PreAuthorize(VIEW)
    public List<DeviceResponse> allDevices() {
        return deviceService.listAll().stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/devices/available")
    @PreAuthorize(VIEW)
    public List<DeviceResponse> availableDevices() {
        return deviceService.listAvailable().stream().map(DeviceResponse::from).toList();
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_DEVICES)
    public DeviceResponse registerDevice(@Valid @RequestBody RegisterDeviceRequest r) {
        return DeviceResponse.from(deviceService.register(r.deviceType(), r.provider(), r.externalDeviceId(),
                r.deviceModel(), r.imei(), r.serialNumber(), r.simNumber(), r.firmwareVersion()));
    }

    @GetMapping("/{vehicleId}/devices")
    @PreAuthorize(VIEW)
    public List<DeviceResponse> devicesOf(@PathVariable long vehicleId) {
        return deviceService.devicesOf(vehicleId).stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/{vehicleId}/devices/history")
    @PreAuthorize(VIEW)
    public List<DeviceAssignmentResponse> deviceHistory(@PathVariable long vehicleId) {
        return deviceService.assignmentHistory(vehicleId).stream().map(DeviceAssignmentResponse::from).toList();
    }

    @PostMapping("/{vehicleId}/devices/assign")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_DEVICES)
    public DeviceAssignmentResponse attachDevice(@PathVariable long vehicleId, @Valid @RequestBody AttachDeviceRequest r) {
        return DeviceAssignmentResponse.from(
                deviceService.attach(vehicleId, r.deviceId(), r.installationDate(), r.primary(), r.remarks()));
    }

    @PostMapping("/{vehicleId}/devices/{deviceId}/detach")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE_DEVICES)
    public void detachDevice(@PathVariable long vehicleId, @PathVariable long deviceId,
                             @RequestBody(required = false) DetachDeviceRequest r) {
        deviceService.detach(vehicleId, deviceId, r == null ? null : r.removalDate(), r == null ? null : r.remarks());
    }
}
