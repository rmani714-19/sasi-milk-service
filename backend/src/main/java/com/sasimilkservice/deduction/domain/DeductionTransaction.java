package com.sasimilkservice.deduction.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
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
 * A fixed-amount deduction against a producer's settlement (cow feed, emergency
 * cash, etc.). Percentage deductions are not supported (client, OQ #6).
 * Advance recovery is handled by the advance ledger, not here.
 */
@Entity
@Table(name = "deduction_transactions")
@Getter
@Setter
public class DeductionTransaction extends AuditableEntity {

    public enum Status { PENDING, APPLIED, CANCELLED }

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "deduction_type_id", nullable = false)
    private Long deductionTypeId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate = LocalDate.now();

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "settlement_item_id")
    private Long settlementItemId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;
}
