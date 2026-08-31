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

/** An INDIVIDUAL producer incentive, per litre, effective-dated. */
@Entity
@Table(name = "producer_incentives")
@Getter
@Setter
public class ProducerIncentive extends AuditableEntity {

    public enum Status { ACTIVE, CANCELLED }

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

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
