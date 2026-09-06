package com.sasimilkservice.center.domain;

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
 * One of the centre's own bank accounts. Added by an authorized user, then
 * VERIFIED (a manual "these details are correct" confirmation) before it can be
 * picked when processing a payment. The account number is encrypted at rest and
 * never returned to clients — only the masked last-4.
 */
@Entity
@Table(name = "center_bank_accounts")
@Getter
@Setter
public class CenterBankAccount extends AuditableEntity {

    public enum Status { ACTIVE, INACTIVE }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    /** AES-GCM encrypted — never returned to clients. */
    @Column(name = "account_number_enc", nullable = false)
    private String accountNumberEnc;

    @Column(name = "account_number_last4", nullable = false)
    private String accountNumberLast4;

    @Column(name = "account_number_hash", nullable = false)
    private String accountNumberHash;

    @Column(nullable = false)
    private String ifsc;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public boolean isUsable() {
        return status == Status.ACTIVE && verified;
    }

    public String maskedNumber() {
        return "********" + (accountNumberLast4 == null ? "" : accountNumberLast4);
    }

    /** Short label for the payment picker / balance check record. */
    public String displayLabel() {
        String base = bankName + " " + maskedNumber();
        return label == null || label.isBlank() ? base : label + " (" + base + ")";
    }
}
