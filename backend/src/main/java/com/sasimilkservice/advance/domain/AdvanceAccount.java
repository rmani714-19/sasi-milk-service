package com.sasimilkservice.advance.domain;

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

/**
 * One advance grant and its running outstanding balance. Recovery is deducted
 * weekly at settlement (client, OQ #7). {@code outstanding_amount} is CHECK-
 * constrained {@code >= 0}; over-recovery is impossible.
 */
@Entity
@Table(name = "advance_accounts")
@Getter
@Setter
public class AdvanceAccount extends AuditableEntity {

    public enum RecoveryMode { FIXED_PER_WEEK, PERCENTAGE_OF_NET }

    public enum Status { ACTIVE, CLOSED, CANCELLED }

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "advance_date", nullable = false)
    private LocalDate advanceDate = LocalDate.now();

    @Column(name = "original_amount", nullable = false)
    private BigDecimal originalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_mode", nullable = false)
    private RecoveryMode recoveryMode;

    @Column(name = "recovery_amount")
    private BigDecimal recoveryAmount;

    @Column(name = "recovery_percentage")
    private BigDecimal recoveryPercentage;

    @Column(name = "outstanding_amount", nullable = false)
    private BigDecimal outstandingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private String description;

    @Column(name = "closed_at")
    private Instant closedAt;
}
