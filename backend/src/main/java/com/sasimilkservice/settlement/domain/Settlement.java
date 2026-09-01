package com.sasimilkservice.settlement.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A weekly settlement run. Lifecycle: DRAFT → CALCULATED → REVIEWED → APPROVED
 * (then payout states). Once APPROVED it is immutable; corrections go through
 * {@link SettlementAdjustment} on a later settlement.
 */
@Entity
@Table(name = "settlements")
@Getter
@Setter
public class Settlement extends AuditableEntity {

    public enum Status {
        DRAFT, CALCULATED, REVIEWED, APPROVED,
        PAYOUT_INITIATED, PARTIALLY_PAID, PAID, FAILED, CANCELLED
    }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "settlement_number", nullable = false, updatable = false)
    private String settlementNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "total_producers", nullable = false)
    private int totalProducers;

    @Column(name = "total_litres", nullable = false)
    private BigDecimal totalLitres = BigDecimal.ZERO;

    @Column(name = "total_milk_amount", nullable = false)
    private BigDecimal totalMilkAmount = BigDecimal.ZERO;

    @Column(name = "total_incentive", nullable = false)
    private BigDecimal totalIncentive = BigDecimal.ZERO;

    @Column(name = "total_deduction", nullable = false)
    private BigDecimal totalDeduction = BigDecimal.ZERO;

    @Column(name = "total_advance_recovery", nullable = false)
    private BigDecimal totalAdvanceRecovery = BigDecimal.ZERO;

    @Column(name = "total_adjustment", nullable = false)
    private BigDecimal totalAdjustment = BigDecimal.ZERO;

    @Column(name = "total_round_off", nullable = false)
    private BigDecimal totalRoundOff = BigDecimal.ZERO;

    @Column(name = "total_net_payable", nullable = false)
    private BigDecimal totalNetPayable = BigDecimal.ZERO;

    @Column(name = "rounding_mode", nullable = false)
    private String roundingMode;

    @Column(name = "rounding_scale", nullable = false)
    private int roundingScale;

    @Column(name = "calc_engine_version")
    private String calcEngineVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> calculationSnapshot = new HashMap<>();

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
