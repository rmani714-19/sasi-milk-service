package com.sasimilkservice.settlement.domain;

import com.sasimilkservice.common.domain.BaseEntity;
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
import java.util.HashMap;
import java.util.Map;

/** Per-producer line of a {@link Settlement}. Mirrors {@code settlement_items}. */
@Entity
@Table(name = "settlement_items")
@Getter
@Setter
public class SettlementItem extends BaseEntity {

    public enum PaymentStatus { PENDING, INITIATED, PROCESSING, SUCCESS, FAILED, ON_HOLD, CANCELLED }

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "bank_account_id")
    private Long bankAccountId;

    @Column(name = "morning_litres", nullable = false)
    private BigDecimal morningLitres = BigDecimal.ZERO;
    @Column(name = "evening_litres", nullable = false)
    private BigDecimal eveningLitres = BigDecimal.ZERO;
    @Column(name = "total_litres", nullable = false)
    private BigDecimal totalLitres = BigDecimal.ZERO;
    @Column(name = "collection_count", nullable = false)
    private int collectionCount;
    @Column(name = "average_rate", nullable = false)
    private BigDecimal averageRate = BigDecimal.ZERO;

    @Column(name = "milk_amount", nullable = false)
    private BigDecimal milkAmount = BigDecimal.ZERO;
    @Column(name = "incentive_amount", nullable = false)
    private BigDecimal incentiveAmount = BigDecimal.ZERO;
    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "feed_deduction", nullable = false)
    private BigDecimal feedDeduction = BigDecimal.ZERO;
    @Column(name = "veterinary_deduction", nullable = false)
    private BigDecimal veterinaryDeduction = BigDecimal.ZERO;
    @Column(name = "other_deduction", nullable = false)
    private BigDecimal otherDeduction = BigDecimal.ZERO;
    @Column(name = "advance_recovery", nullable = false)
    private BigDecimal advanceRecovery = BigDecimal.ZERO;
    @Column(name = "total_deduction", nullable = false)
    private BigDecimal totalDeduction = BigDecimal.ZERO;

    @Column(name = "adjustment_amount", nullable = false)
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;
    @Column(name = "net_before_rounding", nullable = false)
    private BigDecimal netBeforeRounding = BigDecimal.ZERO;
    @Column(name = "round_off", nullable = false)
    private BigDecimal roundOff = BigDecimal.ZERO;
    @Column(name = "net_payable", nullable = false)
    private BigDecimal netPayable = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_reference")
    private String paymentReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> calculationSnapshot = new HashMap<>();
}
