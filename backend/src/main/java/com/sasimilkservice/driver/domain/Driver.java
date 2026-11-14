package com.sasimilkservice.driver.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** A vehicle driver on the payroll. Salary is paid monthly via a salary batch. */
@Entity
@Table(name = "drivers")
@Getter
@Setter
public class Driver extends AuditableEntity {

    public enum SalaryType { FIXED, PER_DAY, CUSTOM }

    public enum Status { ACTIVE, INACTIVE }

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String mobile;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type", nullable = false)
    private SalaryType salaryType = SalaryType.FIXED;

    @Column(name = "monthly_salary", nullable = false)
    private BigDecimal monthlySalary = BigDecimal.ZERO;

    @Column(name = "per_day_rate")
    private BigDecimal perDayRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private String note;

    /** The regions (areas) this driver collects from — their "route". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "driver_route_regions", joinColumns = @JoinColumn(name = "driver_id"))
    @Column(name = "region_id")
    private Set<Long> routeRegionIds = new HashSet<>();
}
