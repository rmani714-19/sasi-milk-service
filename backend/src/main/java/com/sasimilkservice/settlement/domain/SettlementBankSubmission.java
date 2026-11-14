package com.sasimilkservice.settlement.domain;

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

/**
 * Record that a payment run was handed to the bank on a given date. Purely
 * paperwork — the application never moves money; the owner pays through the
 * bank and later confirms each producer manually.
 */
@Entity
@Table(name = "settlement_bank_submissions")
@Getter
@Setter
public class SettlementBankSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, unique = true)
    private Long settlementId;

    @Column(name = "submitted_on", nullable = false)
    private LocalDate submittedOn;

    @Column(name = "bank_reference")
    private String bankReference;

    private String note;

    @Column(name = "producer_count", nullable = false)
    private int producerCount;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
