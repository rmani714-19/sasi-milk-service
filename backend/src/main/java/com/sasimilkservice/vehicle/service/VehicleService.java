package com.sasimilkservice.vehicle.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.ConflictException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.driver.repository.DriverRepository;
import com.sasimilkservice.vehicle.domain.Vehicle;
import com.sasimilkservice.vehicle.domain.VehicleDriverAssignment;
import com.sasimilkservice.vehicle.repository.VehicleDriverAssignmentRepository;
import com.sasimilkservice.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Vehicle master data and driver assignment. Device concerns live in {@link VehicleDeviceService}. */
@Service
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicles;
    private final VehicleDriverAssignmentRepository driverAssignments;
    private final DriverRepository drivers;
    private final AuditRecorder audit;

    public VehicleService(VehicleRepository vehicles, VehicleDriverAssignmentRepository driverAssignments,
                          DriverRepository drivers, AuditRecorder audit) {
        this.vehicles = vehicles;
        this.driverAssignments = driverAssignments;
        this.drivers = drivers;
        this.audit = audit;
    }

    public List<Vehicle> findAll() {
        return vehicles.findAll();
    }

    public Vehicle require(long id) {
        return vehicles.findById(id).orElseThrow(() -> NotFoundException.of("Vehicle", id));
    }

    public List<VehicleDriverAssignment> driverAssignmentHistory(long vehicleId) {
        return driverAssignments.findByVehicleIdOrderByIdDesc(vehicleId);
    }

    @Transactional
    public Vehicle create(String code, String registrationNumber, Vehicle.VehicleType vehicleType,
                          String make, String model, Short modelYear, String description,
                          BigDecimal capacityLitres, LocalDate registrationDate, LocalDate insuranceExpiry,
                          LocalDate pollutionExpiry, String notes, Long driverId) {
        if (vehicles.findByCode(code).isPresent()) {
            throw new ConflictException("Vehicle code '" + code + "' already exists");
        }
        if (vehicles.findByRegistrationNumber(registrationNumber).isPresent()) {
            throw new ConflictException("Vehicle number '" + registrationNumber + "' already exists");
        }
        Vehicle v = new Vehicle();
        v.setCode(code);
        v.setRegistrationNumber(registrationNumber);
        v.setVehicleType(vehicleType == null ? Vehicle.VehicleType.OTHER : vehicleType);
        v.setMake(make);
        v.setModel(model);
        v.setModelYear(modelYear);
        v.setDescription(description);
        v.setCapacityLitres(capacityLitres);
        v.setRegistrationDate(registrationDate);
        v.setInsuranceExpiry(insuranceExpiry);
        v.setPollutionExpiry(pollutionExpiry);
        v.setNotes(notes);
        v = vehicles.save(v);
        audit.record("VEHICLE_CREATED", "Vehicle", v.getId(),
                "Vehicle " + v.getRegistrationNumber() + " (" + v.getCode() + ") created");
        if (driverId != null) {
            assignDriver(v.getId(), driverId, LocalDate.now(), "Assigned at vehicle creation");
        }
        return v;
    }

    @Transactional
    public Vehicle update(long id, Vehicle.VehicleType vehicleType, String make, String model, Short modelYear,
                          String description, BigDecimal capacityLitres, LocalDate registrationDate,
                          LocalDate insuranceExpiry, LocalDate pollutionExpiry, String notes) {
        Vehicle v = require(id);
        if (vehicleType != null) v.setVehicleType(vehicleType);
        v.setMake(make);
        v.setModel(model);
        v.setModelYear(modelYear);
        v.setDescription(description);
        v.setCapacityLitres(capacityLitres);
        v.setRegistrationDate(registrationDate);
        v.setInsuranceExpiry(insuranceExpiry);
        v.setPollutionExpiry(pollutionExpiry);
        v.setNotes(notes);
        audit.record("VEHICLE_UPDATED", "Vehicle", id, "Vehicle " + v.getRegistrationNumber() + " updated");
        return v;
    }

    @Transactional
    public Vehicle changeStatus(long id, Vehicle.Status status) {
        Vehicle v = require(id);
        Vehicle.Status old = v.getStatus();
        if (old == status) {
            return v;
        }
        v.setStatus(status);
        audit.record("VEHICLE_STATUS_CHANGED", "Vehicle", id, old + " -> " + status);
        return v;
    }

    @Transactional
    public VehicleDriverAssignment assignDriver(long vehicleId, long driverId, LocalDate from, String remarks) {
        Vehicle v = require(vehicleId);
        if (!drivers.existsById(driverId)) {
            throw NotFoundException.of("Driver", driverId);
        }
        if (from == null) {
            from = LocalDate.now();
        }
        LocalDate assignedFrom = from;
        driverAssignments.findByVehicleIdAndAssignedToIsNull(vehicleId).ifPresent(open -> {
            if (Objects.equals(open.getDriverId(), driverId)) {
                throw new BusinessRuleException("This driver is already assigned to the vehicle");
            }
            open.setAssignedTo(assignedFrom);
        });

        VehicleDriverAssignment a = new VehicleDriverAssignment();
        a.setVehicleId(vehicleId);
        a.setDriverId(driverId);
        a.setAssignedFrom(assignedFrom);
        a.setRemarks(remarks);
        CurrentUser.id().ifPresent(a::setAssignedBy);
        a = driverAssignments.save(a);

        v.setDriverId(driverId);
        audit.record("VEHICLE_DRIVER_ASSIGNED", "Vehicle", vehicleId,
                "Driver " + driverId + " assigned from " + assignedFrom);
        return a;
    }

    @Transactional
    public void unassignDriver(long vehicleId, LocalDate to) {
        Vehicle v = require(vehicleId);
        VehicleDriverAssignment open = driverAssignments.findByVehicleIdAndAssignedToIsNull(vehicleId)
                .orElseThrow(() -> new BusinessRuleException("No driver is currently assigned to this vehicle"));
        open.setAssignedTo(to == null ? LocalDate.now() : to);
        v.setDriverId(null);
        audit.record("VEHICLE_DRIVER_UNASSIGNED", "Vehicle", vehicleId,
                "Driver " + open.getDriverId() + " unassigned on " + open.getAssignedTo());
    }
}
