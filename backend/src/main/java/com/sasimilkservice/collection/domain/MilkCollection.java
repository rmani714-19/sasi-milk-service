package com.sasimilkservice.collection.domain;

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
 * One producer milk submission. The rate/incentive fields are a snapshot taken at
 * creation time and are NEVER recomputed when rates or incentives later change
 * (spec milk_collection.rules).
 */
@Entity
@Table(name = "milk_collections")
@Getter
@Setter
public class MilkCollection extends AuditableEntity {

    public enum Status { DRAFT, CONFIRMED, VOID, PENDING_QUALITY }

    public enum Source { ONLINE, OFFLINE_SYNC, IMPORT }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "client_transaction_id", nullable = false, updatable = false)
    private UUID clientTransactionId;

    @Column(name = "collection_number", nullable = false, updatable = false)
    private String collectionNumber;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(nullable = false)
    private String shift;

    @Column(name = "milk_quantity_litres", nullable = false)
    private BigDecimal milkQuantityLitres;

    /** Null until quality is entered (status PENDING_QUALITY). */
    @Column(name = "fat_percentage")
    private BigDecimal fatPercentage;

    /** Null until quality is entered (status PENDING_QUALITY). */
    @Column(name = "snf_percentage")
    private BigDecimal snfPercentage;

    @Column(name = "rate_chart_id")
    private Long rateChartId;

    @Column(name = "ts_value_applied", nullable = false)
    private BigDecimal tsValueApplied;

    @Column(name = "rate_scope_applied")
    private String rateScopeApplied;

    @Column(name = "applicable_rate", nullable = false)
    private BigDecimal applicableRate;

    @Column(name = "incentive_rate_applied", nullable = false)
    private BigDecimal incentiveRateApplied = BigDecimal.ZERO;

    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    @Column(name = "incentive_amount", nullable = false)
    private BigDecimal incentiveAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> calculationSnapshot = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.CONFIRMED;

    @Column(name = "settlement_item_id")
    private Long settlementItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source = Source.ONLINE;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    public boolean isQualityPending() {
        return status == Status.PENDING_QUALITY;
    }
}
