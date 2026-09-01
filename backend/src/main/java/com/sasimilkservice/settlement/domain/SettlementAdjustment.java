package com.sasimilkservice.settlement.domain;

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

/**
 * A manual credit/debit applied to a producer within a settlement — the only way
 * to correct an already-APPROVED settlement (via {@code originSettlementId}
 * pointing back at it).
 */
@Entity
@Table(name = "settlement_adjustments")
@Getter
@Setter
public class SettlementAdjustment {

    public enum Type { CREDIT, DEBIT }

    public enum Status { ACTIVE, REVERSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "settlement_item_id")
    private Long settlementItemId;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private Type adjustmentType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    @Column(name = "origin_settlement_id")
    private Long originSettlementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    /** Signed contribution to net: +amount for CREDIT, -amount for DEBIT. */
    public BigDecimal signedAmount() {
        return adjustmentType == Type.CREDIT ? amount : amount.negate();
    }
}
