package com.sasimilkservice.driversalary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** The record of handing a driver salary batch to the bank. Nothing is sent from the application. */
@Entity
@Table(name = "driver_salary_bank_submissions")
@Getter
@Setter
public class DriverSalaryBankSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, unique = true)
    private Long batchId;

    @Column(name = "submitted_on", nullable = false)
    private LocalDate submittedOn;

    @Column(name = "bank_reference")
    private String bankReference;

    private String note;

    @Column(name = "driver_count", nullable = false)
    private int driverCount;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
