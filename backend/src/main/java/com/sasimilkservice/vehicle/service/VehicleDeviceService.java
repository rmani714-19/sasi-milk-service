package com.sasimilkservice.vehicle.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.ConflictException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.vehicle.domain.VehicleDevice;
import com.sasimilkservice.vehicle.domain.VehicleDeviceAssignment;
import com.sasimilkservice.vehicle.repository.VehicleDeviceAssignmentRepository;
import com.sasimilkservice.vehicle.repository.VehicleDeviceRepository;
import com.sasimilkservice.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** GPS/camera hardware master data and the attach/detach flow with full history. */
@Service
@Transactional(readOnly = true)
public class VehicleDeviceService {

    private final VehicleDeviceRepository devices;
    private final VehicleDeviceAssignmentRepository assignments;
    private final VehicleRepository vehicles;
    private final AuditRecorder audit;

    public VehicleDeviceService(VehicleDeviceRepository devices, VehicleDeviceAssignmentRepository assignments,
                                VehicleRepository vehicles, AuditRecorder audit) {
        this.devices = devices;
        this.assignments = assignments;
        this.vehicles = vehicles;
        this.audit = audit;
    }

    public List<VehicleDevice> listAll() {
        return devices.findAllByOrderByIdDesc();
    }

    public List<VehicleDevice> listAvailable() {
        return devices.findByVehicleIdIsNullAndStatus(VehicleDevice.Status.ACTIVE);
    }

    public List<VehicleDevice> devicesOf(long vehicleId) {
        return devices.findByVehicleId(vehicleId);
    }

    public VehicleDevice require(long id) {
        return devices.findById(id).orElseThrow(() -> NotFoundException.of("VehicleDevice", id));
    }

    public List<VehicleDeviceAssignment> assignmentHistory(long vehicleId) {
        return assignments.findByVehicleIdOrderByIdDesc(vehicleId);
    }

    @Transactional
    public VehicleDevice register(VehicleDevice.DeviceType deviceType, String provider, String externalDeviceId,
                                  String deviceModel, String imei, String serialNumber, String rawSimNumber,
                                  String firmwareVersion) {
        if (imei != null && !imei.isBlank() && devices.findByImei(imei).isPresent()) {
            throw new ConflictException("A device with IMEI '" + imei + "' is already registered");
        }
        VehicleDevice d = new VehicleDevice();
        d.setDeviceType(deviceType);
        d.setProvider(provider);
        d.setExternalDeviceId(externalDeviceId);
        d.setDeviceModel(deviceModel);
        d.setImei(imei);
        d.setSerialNumber(serialNumber);
        d.setSimNumberMasked(maskSim(rawSimNumber));
        d.setFirmwareVersion(firmwareVersion);
        d = devices.save(d);
        audit.record("VEHICLE_DEVICE_REGISTERED", "VehicleDevice", d.getId(),
                deviceType + " device " + externalDeviceId + " registered");
        return d;
    }

    @Transactional
    public VehicleDeviceAssignment attach(long vehicleId, long deviceId, LocalDate installationDate,
                                          boolean primary, String remarks) {
        if (!vehicles.existsById(vehicleId)) {
            throw NotFoundException.of("Vehicle", vehicleId);
        }
        VehicleDevice d = require(deviceId);
        if (d.getVehicleId() != null) {
            throw new ConflictException("This device is already attached to a vehicle — detach it first");
        }
        if (d.getStatus() != VehicleDevice.Status.ACTIVE) {
            throw new BusinessRuleException("Only an ACTIVE device can be attached");
        }
        LocalDate from = installationDate == null ? LocalDate.now() : installationDate;

        if (primary) {
            devices.findByVehicleIdAndStatus(vehicleId, VehicleDevice.Status.ACTIVE).stream()
                    .filter(VehicleDevice::isPrimary)
                    .forEach(existing -> existing.setPrimary(false));
        }

        d.setVehicleId(vehicleId);
        d.setInstallationDate(from);
        d.setRemovedDate(null);
        d.setPrimary(primary);

        VehicleDeviceAssignment a = new VehicleDeviceAssignment();
        a.setVehicleId(vehicleId);
        a.setDeviceId(deviceId);
        a.setAssignedFrom(from);
        a.setRemarks(remarks);
        CurrentUser.id().ifPresent(a::setAssignedBy);
        a = assignments.save(a);

        audit.record("VEHICLE_DEVICE_ATTACHED", "Vehicle", vehicleId,
                "Device " + deviceId + " (" + d.getExternalDeviceId() + ") attached " + from);
        return a;
    }

    @Transactional
    public void detach(long vehicleId, long deviceId, LocalDate removalDate, String remarks) {
        VehicleDevice d = require(deviceId);
        if (!Objects.equals(d.getVehicleId(), vehicleId)) {
            throw new BusinessRuleException("This device is not currently attached to this vehicle");
        }
        VehicleDeviceAssignment open = assignments.findByDeviceIdAndAssignedToIsNull(deviceId)
                .orElseThrow(() -> new BusinessRuleException("This device has no active assignment to close"));
        LocalDate to = removalDate == null ? LocalDate.now() : removalDate;
        open.setAssignedTo(to);
        CurrentUser.id().ifPresent(open::setRemovedBy);
        if (remarks != null && !remarks.isBlank()) {
            open.setRemarks(remarks);
        }

        d.setVehicleId(null);
        d.setRemovedDate(to);
        d.setPrimary(false);

        audit.record("VEHICLE_DEVICE_DETACHED", "Vehicle", vehicleId,
                "Device " + deviceId + " detached " + to + (remarks == null || remarks.isBlank() ? "" : ": " + remarks));
    }

    /** Store only a masked value — the raw SIM number is never persisted. */
    private static String maskSim(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() <= 4) {
            return "*".repeat(digits.length());
        }
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }
}
