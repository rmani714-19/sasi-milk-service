package com.sasimilkservice.vehicle.domain;

import com.sasimilkservice.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Collection vehicle (Phase 2). GPS/camera integration is via provider adapters. */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
public class Vehicle extends BaseEntity {

    public enum Status { ACTIVE, INACTIVE }

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    private String description;

    @Column(name = "capacity_litres")
    private BigDecimal capacityLitres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
}
