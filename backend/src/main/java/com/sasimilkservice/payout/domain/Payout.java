package com.sasimilkservice.payout.domain;

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

/**
 * One disbursement to one producer for one settlement item. Exactly one payout
 * per settlement item ({@code settlement_item_id} UNIQUE) and the provider is
 * called at most once per {@code idempotency_key}.
 */
@Entity
@Table(name = "payouts")
@Getter
@Setter
public class Payout extends AuditableEntity {

    public enum Status { PENDING, INITIATED, PROCESSING, SUCCESS, FAILED, CANCELLED, REVERSED }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "settlement_item_id", nullable = false, updatable = false)
    private Long settlementItemId;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "INR";

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_payout_id")
    private String providerPayoutId;

    @Column(name = "provider_ref")
    private String providerRef;

    private String utr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "settled_at")
    private Instant settledAt;
}
