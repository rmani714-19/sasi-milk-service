package com.sasimilkservice.user.domain;

import com.sasimilkservice.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * A named set of permission codes. Seeded in {@code V10}; the {@code ADMIN} role
 * holds the {@code ALL} sentinel. Lives in the {@code user} module because roles
 * and users form one security aggregate.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> permissions = new ArrayList<>();

    @Column(name = "is_system", nullable = false)
    private boolean system;
}
