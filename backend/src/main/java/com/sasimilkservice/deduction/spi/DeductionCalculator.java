package com.sasimilkservice.deduction.spi;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregates a producer's pending deductions for a settlement period. Published
 * SPI of the {@code deduction} module.
 *
 * <p>Deductions are always a <strong>fixed rupee amount</strong> (client, OQ #6:
 * "not percentage" — cow feed, emergency cash, etc.). There is no percentage
 * mode and no calculation base. This calculator simply sums the pending
 * {@code deduction_transactions} and buckets them by type for the settlement row.
 *
 * <p>Advance recovery is <em>not</em> handled here — it flows through
 * {@link com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator} and its own
 * ledger (spec advances.rules; client OQ #7).
 *
 * <p>Pure and deterministic.
 */
public interface DeductionCalculator {

    DeductionResult calculate(DeductionInput input);

    /**
     * @param pending pending deduction transactions for the producer in the period
     */
    record DeductionInput(List<PendingDeduction> pending) {
    }

    /**
     * @param deductionTransactionId source row id
     * @param typeCode               deduction_types.code (FEED, VETERINARY, EMERGENCY, OTHER)
     * @param amount                 fixed rupee amount (&ge; 0)
     */
    record PendingDeduction(long deductionTransactionId, String typeCode, BigDecimal amount) {
    }

    /**
     * @param lines               echo of each applied transaction (for the snapshot + audit)
     * @param feedDeduction       Σ typeCode = FEED
     * @param veterinaryDeduction Σ typeCode = VETERINARY
     * @param otherDeduction      Σ everything else (EMERGENCY, OTHER, ...)
     * @param totalDeduction      feed + veterinary + other (advance recovery added by the settlement engine)
     */
    record DeductionResult(
            List<AppliedDeduction> lines,
            BigDecimal feedDeduction,
            BigDecimal veterinaryDeduction,
            BigDecimal otherDeduction,
            BigDecimal totalDeduction) {
    }

    record AppliedDeduction(long deductionTransactionId, String typeCode, BigDecimal amount) {
    }
}
