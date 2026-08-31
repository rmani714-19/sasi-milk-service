package com.sasimilkservice.producer.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Producer bank account. The account number is stored AES-GCM encrypted
 * ({@code accountNumberEnc}); {@code accountNumberLast4} + {@code accountNumberHash}
 * support masked display and lookup without decryption.
 */
@Entity
@Table(name = "producer_bank_accounts")
@Getter
@Setter
public class ProducerBankAccount extends AuditableEntity {

    public enum Status { ACTIVE, INACTIVE }

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "account_number_enc", nullable = false)
    private String accountNumberEnc;

    @Column(name = "account_number_last4", nullable = false)
    private String accountNumberLast4;

    @Column(name = "account_number_hash", nullable = false)
    private String accountNumberHash;

    @Column(name = "ifsc_code", nullable = false)
    private String ifscCode;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    public String masked() {
        return "XXXXXX" + accountNumberLast4;
    }
}
