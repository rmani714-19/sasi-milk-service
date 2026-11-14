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

/** One driver's salary line inside a {@link DriverSalaryBatch}. */
@Entity
@Table(name = "driver_salary_items")
@Getter
@Setter
public class DriverSalaryItem extends AuditableEntity {

    public enum PaymentStatus { PENDING, PROCESSING, PAID, FAILED, ON_HOLD }

    public enum PaymentMethod { BANK_TRANSFER, CASH, OTHER }

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "salary_type", nullable = false)
    private String salaryType;

    // earnings
    @Column(name = "basic_salary", nullable = false)
    private BigDecimal basicSalary = BigDecimal.ZERO;
    @Column(name = "working_days", nullable = false)
    private BigDecimal workingDays = new BigDecimal("30");
    @Column(nullable = false)
    private BigDecimal allowances = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal overtime = BigDecimal.ZERO;
    @Column(name = "other_earnings", nullable = false)
    private BigDecimal otherEarnings = BigDecimal.ZERO;

    // informational snapshots taken when the batch is calculated (not part of the pay calculation)
    @Column(name = "route_litres", nullable = false)
    private BigDecimal routeLitres = BigDecimal.ZERO;
    @Column(name = "vehicle_expenses", nullable = false)
    private BigDecimal vehicleExpenses = BigDecimal.ZERO;

    // deductions
    @Column(name = "salary_advance", nullable = false)
    private BigDecimal salaryAdvance = BigDecimal.ZERO;
    @Column(name = "loan_recovery", nullable = false)
    private BigDecimal loanRecovery = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal penalty = BigDecimal.ZERO;
    @Column(name = "other_deduction", nullable = false)
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    // totals
    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount = BigDecimal.ZERO;
    @Column(name = "total_deduction", nullable = false)
    private BigDecimal totalDeduction = BigDecimal.ZERO;
    @Column(name = "net_salary", nullable = false)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference")
    private String paymentReference;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "paid_by")
    private Long paidBy;
    @Column(name = "failure_reason")
    private String failureReason;
    private String note;
}
