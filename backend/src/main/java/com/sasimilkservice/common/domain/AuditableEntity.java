package com.sasimilkservice.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * {@link BaseEntity} plus optimistic-lock {@code version} and {@code created_by} /
 * {@code updated_by} user ids (populated from the security context by
 * {@code auditorAware}). Used for every mutable financial aggregate root
 * (spec financial_accuracy: "Use optimistic locking where concurrent modification
 * is possible").
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
