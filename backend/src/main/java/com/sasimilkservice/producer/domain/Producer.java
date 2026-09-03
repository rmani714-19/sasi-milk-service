package com.sasimilkservice.producer.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Producer master. {@code region_id} is kept as a scalar FK (not a {@code @ManyToOne}
 * to {@code region.Region}) to respect the module boundary rule — the region is
 * validated through {@code RegionDirectory}.
 */
@Entity
@Table(name = "producers")
@Getter
@Setter
public class Producer extends AuditableEntity {

    public enum Status { ACTIVE, INACTIVE }

    /** How often this producer is paid for their milk. */
    public enum PaymentFrequency { WEEKLY, MONTHLY }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "producer_number", nullable = false, unique = true)
    private String producerNumber;

    @Column(name = "producer_name", nullable = false)
    private String producerName;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "alternate_mobile")
    private String alternateMobile;

    @Column(name = "upi_id")
    private String upiId;

    private String email;

    private String address;

    // Aadhaar: encrypted at rest, masked in responses (never returned in full)
    @Column(name = "aadhaar_enc")
    private String aadhaarEnc;

    @Column(name = "aadhaar_last4")
    private String aadhaarLast4;

    @Column(name = "aadhaar_hash")
    private String aadhaarHash;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", nullable = false)
    private PaymentFrequency paymentFrequency = PaymentFrequency.WEEKLY;

    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    private String notes;
}
