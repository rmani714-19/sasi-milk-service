package com.sasimilkservice.deduction.spi;

import com.sasimilkservice.deduction.spi.DeductionCalculator.DeductionResult;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Published SPI of the {@code deduction} module for the settlement engine:
 * fetch a producer's pending (non-advance) deductions as of a date, and mark
 * them applied against a settlement item.
 */
public interface DeductionLedger {

    /** Resolve pending deductions for the producer as of {@code asOf} into a settlement-ready result. */
    PendingDeductions pendingFor(long producerId, LocalDate asOf);

    /** Mark the given deduction transactions APPLIED and link them to the settlement item. */
    void markApplied(Collection<Long> deductionTransactionIds, long settlementItemId);

    /** Reverse an earlier {@link #markApplied}: APPLIED → PENDING, unlink from the settlement item. */
    void unapply(long settlementItemId);

    /**
     * @param transactionIds the source rows (to be linked on apply)
     * @param result         bucketed totals + per-line amounts for the settlement snapshot
     */
    record PendingDeductions(List<Long> transactionIds, DeductionResult result) {
    }
}
