package com.sasimilkservice.driver.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.driver.domain.Driver;
import com.sasimilkservice.driver.repository.DriverRepository;
import com.sasimilkservice.region.spi.RegionDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository drivers;
    private final AuditRecorder audit;
    private final RegionDirectory regions;

    public DriverService(DriverRepository drivers, AuditRecorder audit, RegionDirectory regions) {
        this.drivers = drivers;
        this.audit = audit;
        this.regions = regions;
    }

    /** Set the regions (areas) this driver collects from. Replaces the previous route. */
    @Transactional
    public Driver setRoute(long id, java.util.Collection<Long> regionIds) {
        Driver d = require(id);
        java.util.Set<Long> wanted = new java.util.HashSet<>(regionIds == null ? java.util.List.of() : regionIds);
        for (Long r : wanted) {
            if (r == null || !regions.isActive(r)) {
                throw new BusinessRuleException("Region " + r + " is not an active region");
            }
        }
        d.getRouteRegionIds().clear();
        d.getRouteRegionIds().addAll(wanted);
        audit.record("DRIVER_UPDATED", "Driver", id,
                "Driver %s route set to %d region(s)".formatted(d.getCode(), wanted.size()));
        return d;
    }

    public List<Driver> findAll() {
        return drivers.findAllByOrderByNameAsc();
    }

    public List<Driver> findActive() {
        return drivers.findByStatusOrderByNameAsc(Driver.Status.ACTIVE);
    }

    public Driver require(long id) {
        return drivers.findById(id).orElseThrow(() -> NotFoundException.of("Driver", id));
    }

    @Transactional
    public Driver create(String name, String mobile, LocalDate joiningDate, Long vehicleId,
                         Driver.SalaryType salaryType, BigDecimal monthlySalary, BigDecimal perDayRate,
                         String note) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Driver name is required");
        }
        Driver d = new Driver();
        d.setCode("D%04d".formatted(drivers.nextDriverNumberValue()));
        applyFields(d, name, mobile, joiningDate, vehicleId, salaryType, monthlySalary, perDayRate, note);
        d = drivers.save(d);
        audit.record("DRIVER_CREATED", "Driver", d.getId(), "Driver %s (%s) added".formatted(d.getName(), d.getCode()));
        return d;
    }

    @Transactional
    public Driver update(long id, String name, String mobile, LocalDate joiningDate, Long vehicleId,
                         Driver.SalaryType salaryType, BigDecimal monthlySalary, BigDecimal perDayRate,
                         Driver.Status status, String note) {
        Driver d = require(id);
        applyFields(d, name, mobile, joiningDate, vehicleId, salaryType, monthlySalary, perDayRate, note);
        if (status != null) {
            d.setStatus(status);
        }
        audit.record("DRIVER_UPDATED", "Driver", id, "Driver %s updated".formatted(d.getCode()));
        return d;
    }

    @Transactional
    public void deactivate(long id) {
        Driver d = require(id);
        d.setStatus(Driver.Status.INACTIVE);
        audit.record("DRIVER_UPDATED", "Driver", id, "Driver %s deactivated".formatted(d.getCode()));
    }

    private void applyFields(Driver d, String name, String mobile, LocalDate joiningDate, Long vehicleId,
                             Driver.SalaryType salaryType, BigDecimal monthlySalary, BigDecimal perDayRate, String note) {
        d.setName(name.trim());
        d.setMobile(mobile == null || mobile.isBlank() ? null : mobile.trim());
        d.setJoiningDate(joiningDate);
        d.setVehicleId(vehicleId);
        d.setSalaryType(salaryType == null ? Driver.SalaryType.FIXED : salaryType);
        d.setMonthlySalary(monthlySalary == null ? BigDecimal.ZERO : monthlySalary.max(BigDecimal.ZERO));
        d.setPerDayRate(perDayRate == null ? null : perDayRate.max(BigDecimal.ZERO));
        d.setNote(note == null || note.isBlank() ? null : note.trim());
    }
}
