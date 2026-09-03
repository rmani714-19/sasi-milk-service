package com.sasimilkservice.driversalary.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A monthly driver-salary run. Calculate → Review → Approve → Pay (per item) → Confirm. */
@Entity
@Table(name = "driver_salary_batches")
@Getter
@Setter
public class DriverSalaryBatch extends AuditableEntity {

    public enum Status {
        DRAFT, CALCULATED, REVIEWED, APPROVED,
        PAYMENT_INITIATED, PARTIALLY_PAID, PAID, FAILED, CANCELLED
    }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "batch_number", nullable = false, updatable = false)
    private String batchNumber;

    @Column(name = "batch_type", nullable = false)
    private String batchType = "DRIVER_MONTHLY";

    /** first day of the salary month */
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "total_drivers", nullable = false)
    private int totalDrivers = 0;

    @Column(name = "total_gross", nullable = false)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_allowances", nullable = false)
    private BigDecimal totalAllowances = BigDecimal.ZERO;

    @Column(name = "total_overtime", nullable = false)
    private BigDecimal totalOvertime = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "total_paid", nullable = false)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "calculated_at")
    private Instant calculatedAt;
    @Column(name = "calculated_by")
    private Long calculatedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "reviewed_by")
    private Long reviewedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approved_by")
    private Long approvedBy;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "cancelled_by")
    private Long cancelledBy;
}
