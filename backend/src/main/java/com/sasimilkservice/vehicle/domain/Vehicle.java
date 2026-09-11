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
import java.time.LocalDate;

/**
 * A collection/logistics vehicle. GPS/camera hardware is mapped via
 * {@link VehicleDevice}; the current driver is a denormalized pointer kept in
 * sync with {@link VehicleDriverAssignment} history (assign/unassign go
 * through the service, never a direct setter from the API layer).
 */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
public class Vehicle extends BaseEntity {

    public enum Status { ACTIVE, INACTIVE, MAINTENANCE }

    public enum VehicleType { CAR, VAN, MINI_TRUCK, TRUCK, OTHER }

    @Column(nullable = false, unique = true)
    private String code;

    /** The vehicle's registration plate number — the spec's "vehicle number". */
    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    private String description;

    @Column(name = "capacity_litres")
    private BigDecimal capacityLitres;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType = VehicleType.OTHER;

    private String make;
    private String model;

    @Column(name = "model_year")
    private Short modelYear;

    /** Current driver — kept in sync by VehicleService alongside the assignment history. */
    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "pollution_expiry")
    private LocalDate pollutionExpiry;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
}
