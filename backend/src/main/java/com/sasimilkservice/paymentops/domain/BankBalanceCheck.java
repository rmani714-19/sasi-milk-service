package com.sasimilkservice.paymentops.domain;

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
import java.util.UUID;

/** A recorded check that the owner's bank balance covers a payment batch. */
@Entity
@Table(name = "payment_bank_balance_checks")
@Getter
@Setter
public class BankBalanceCheck extends AuditableEntity {

    public enum SubjectType { SETTLEMENT, SALARY_BATCH }

    public enum Method { MANUAL, AUTOMATIC }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "bank_account_label", nullable = false)
    private String bankAccountLabel;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "required_amount", nullable = false)
    private BigDecimal requiredAmount = BigDecimal.ZERO;

    @Column(name = "remaining_after", nullable = false)
    private BigDecimal remainingAfter = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean sufficient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method = Method.MANUAL;

    private String note;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt = Instant.now();
}
