package com.sasimilkservice.payout.provider.razorpayx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Cached provider recipient mapping — one contact + fund account per bank
 * account, so they are created once and reused across payouts. The account
 * number is not stored, only its blind-index hash.
 */
@Entity
@Table(name = "payment_provider_accounts")
@Getter
@Setter
public class PaymentProviderAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "account_number_hash", nullable = false)
    private String accountNumberHash;

    @Column(nullable = false)
    private String ifsc;

    @Column(name = "contact_id", nullable = false)
    private String contactId;

    @Column(name = "fund_account_id", nullable = false)
    private String fundAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
