package com.sasimilkservice.rate.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An effective-dated rate coefficient. The milk rate is a formula
 * {@code (FAT% + SNF%) * ts_value / 100} — this row supplies {@code ts_value} for a
 * scope ({@code COMMON} or {@code REGION}). The DB enforces that no two ACTIVE rows
 * overlap for the same scope/region ({@code ex_rate_chart_no_overlap}).
 */
@Entity
@Table(name = "rate_charts")
@Getter
@Setter
public class RateChart extends AuditableEntity {

    public enum Scope { COMMON, REGION }

    public enum Status { DRAFT, ACTIVE, ARCHIVED }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    @Column(name = "region_id")
    private Long regionId;

    private String name;

    @Column(name = "ts_value", nullable = false)
    private BigDecimal tsValue;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private String note;
}
