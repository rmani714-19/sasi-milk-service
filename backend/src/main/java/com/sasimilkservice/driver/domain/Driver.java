package com.sasimilkservice.driver.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
