package com.sasimilkservice.incentive.domain;

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
import java.util.UUID;

/**
 * A managed incentive definition with an inclusive effective period. This is a
 * business catalog — it does not alter the collection-time incentive engine,
 * rate calculation or settlement math.
 */
@Entity
@Table(name = "incentives")
@Getter
@Setter
public class Incentive extends AuditableEntity {

    public enum Target { PRODUCER, MEDIATOR }

    public enum IncentiveType {
        PER_LITRE, FIXED_AMOUNT, VOLUME_BONUS, DAILY_BONUS, WEEKLY_BONUS,
        MONTHLY_BONUS, RETENTION_BONUS, NEW_PRODUCER_BONUS, COMPETITOR_RETENTION
    }

    /** Manual switch only. EXPIRED is derived (see {@link #effectiveStatus}). */
    public enum Status { ACTIVE, INACTIVE }

    /** Status including the derived EXPIRED state — used in responses, never persisted. */
    public enum EffectiveStatus { ACTIVE, INACTIVE, EXPIRED }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Target target = Target.PRODUCER;

    @Enumerated(EnumType.STRING)
    @Column(name = "incentive_type", nullable = false)
    private IncentiveType incentiveType = IncentiveType.PER_LITRE;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private String description;

    @Column(nullable = false)
    private int priority = 0;

    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "activated_by")
    private Long activatedBy;
    @Column(name = "deactivated_at")
    private Instant deactivatedAt;
    @Column(name = "deactivated_by")
    private Long deactivatedBy;

    /** True once the incentive could have applied to real transactions (activated and started). */
    public boolean isUsed(LocalDate today) {
        return activatedAt != null && !effectiveFrom.isAfter(today);
    }

    public EffectiveStatus effectiveStatus(LocalDate today) {
        if (status == Status.INACTIVE) {
            return EffectiveStatus.INACTIVE;
        }
        return effectiveTo.isBefore(today) ? EffectiveStatus.EXPIRED : EffectiveStatus.ACTIVE;
    }
}
