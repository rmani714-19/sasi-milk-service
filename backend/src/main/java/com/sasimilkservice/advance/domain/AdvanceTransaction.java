package com.sasimilkservice.advance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only advance ledger entry. {@code amount} is a positive magnitude;
 * {@code txnType} gives the direction. {@code balanceAfter} is the account's
 * outstanding after this entry (CHECK {@code >= 0}).
 */
@Entity
@Table(name = "advance_transactions")
@Getter
@Setter
public class AdvanceTransaction {

    public enum Type { DISBURSEMENT, RECOVERY, ADJUSTMENT, WRITE_OFF, REVERSAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advance_account_id", nullable = false)
    private Long advanceAccountId;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false)
    private Type txnType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate = LocalDate.now();

    @Column(name = "settlement_item_id")
    private Long settlementItemId;

    private String reference;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;
}
