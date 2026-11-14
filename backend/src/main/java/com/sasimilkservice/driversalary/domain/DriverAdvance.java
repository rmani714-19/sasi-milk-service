package com.sasimilkservice.driversalary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Money given to a driver ahead of salary. It stays OPEN until a salary run that deducts it is approved,
 * then DEDUCTED (linked to that salary line). An advance given by mistake can be CANCELLED while still OPEN.
 */
@Entity
@Table(name = "driver_advances")
@Getter
@Setter
public class DriverAdvance {

    public enum Status { OPEN, DEDUCTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "advance_date", nullable = false)
    private LocalDate advanceDate = LocalDate.now();

    @Column(nullable = false)
    private BigDecimal amount;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "deducted_in_item_id")
    private Long deductedInItemId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;
}
