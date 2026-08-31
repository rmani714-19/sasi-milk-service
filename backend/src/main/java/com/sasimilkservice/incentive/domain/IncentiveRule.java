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

/** A COMMON (optionally region-scoped) incentive, per litre, effective-dated. */
@Entity
@Table(name = "incentive_rules")
@Getter
@Setter
public class IncentiveRule extends AuditableEntity {

    public enum Scope { COMMON, REGION }

    public enum Status { ACTIVE, CANCELLED }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope = Scope.COMMON;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "incentive_rate_per_litre", nullable = false)
    private BigDecimal incentiveRatePerLitre;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;
}
