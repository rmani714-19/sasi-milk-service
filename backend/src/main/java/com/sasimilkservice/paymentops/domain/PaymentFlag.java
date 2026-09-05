package com.sasimilkservice.paymentops.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A follow-up flag raised against one payment-activity row (a farmer payout,
 * a driver-salary line, or an expense) — the owner's "something looks wrong
 * here, I need to check it" note. At most one OPEN flag exists per subject at
 * a time (enforced by a partial unique index); resolving it closes it out but
 * keeps the row for history.
 */
@Entity
@Table(name = "payment_flags")
@Getter
@Setter
public class PaymentFlag extends AuditableEntity {

    public enum SubjectType { PAYOUT, SALARY_ITEM, EXPENSE }

    public enum Status { OPEN, RESOLVED }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(nullable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "flagged_by")
    private Long flaggedBy;

    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt = Instant.now();

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
