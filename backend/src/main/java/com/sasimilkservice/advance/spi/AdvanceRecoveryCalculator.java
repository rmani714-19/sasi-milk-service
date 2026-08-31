package com.sasimilkservice.advance.spi;

import java.math.BigDecimal;
import java.util.List;

/**
 * Computes how much to recover this week against a producer's outstanding
 * advances. Published SPI of the {@code advance} module.
 *
 * <p>Per active {@code advance_accounts} row (spec advances):
 * <ul>
 *   <li>{@code FIXED_PER_WEEK}    — candidate = {@code recovery_amount}</li>
 *   <li>{@code PERCENTAGE_OF_NET} — candidate = {@code netAvailable * recovery_percentage / 100}</li>
 * </ul>
 * Actual recovery for the account = {@code min(candidate, outstanding, netAvailable)}
 * and is applied oldest-account-first until {@code netAvailable} is exhausted.
 *
 * <p>Invariants (spec advances.rules):
 * <ul>
 *   <li>Outstanding can never go negative — {@code balance_after >= 0}.</li>
 *   <li>Over-recovery is impossible — recovery is capped at outstanding.</li>
 *   <li>Recovery never drives the producer's net payable below zero
 *       (OPEN_QUESTIONS #7: partial-recovery behaviour to be confirmed).</li>
 *   <li>Each recovery becomes one {@code advance_transactions} row of type RECOVERY,
 *       linked to the settlement item.</li>
 * </ul>
 *
 * <p>Pure and deterministic — the caller persists the ledger rows and updates
 * {@code outstanding_amount} / account {@code status}.
 */
public interface AdvanceRecoveryCalculator {

    AdvanceRecoveryResult calculate(AdvanceRecoveryInput input);

    enum RecoveryMode { FIXED_PER_WEEK, PERCENTAGE_OF_NET }

    /**
     * @param accounts     active advance accounts for the producer, ordered oldest first
     * @param netAvailable net payable so far (gross - non-advance deductions + adjustments);
     *                     recovery will not exceed this
     */
    record AdvanceRecoveryInput(List<AdvanceAccountState> accounts, BigDecimal netAvailable) {
    }

    /**
     * @param advanceAccountId   account id
     * @param outstandingAmount  current outstanding (&gt; 0)
     * @param mode               recovery mode
     * @param recoveryAmount     set when mode == FIXED_PER_WEEK
     * @param recoveryPercentage set when mode == PERCENTAGE_OF_NET
     */
    record AdvanceAccountState(
            long advanceAccountId,
            BigDecimal outstandingAmount,
            RecoveryMode mode,
            BigDecimal recoveryAmount,
            BigDecimal recoveryPercentage) {
    }

    /**
     * @param recoveries        per-account amounts to post to the ledger
     * @param totalRecovery     sum of all recoveries (feeds settlement_items.advance_recovery)
     */
    record AdvanceRecoveryResult(List<AccountRecovery> recoveries, BigDecimal totalRecovery) {
    }

    /**
     * @param advanceAccountId account
     * @param recoveredAmount  amount to recover now (&ge; 0)
     * @param balanceAfter     outstanding after this recovery (&ge; 0)
     * @param closesAccount    true when balanceAfter == 0 -&gt; account status becomes CLOSED
     */
    record AccountRecovery(
            long advanceAccountId,
            BigDecimal recoveredAmount,
            BigDecimal balanceAfter,
            boolean closesAccount) {
    }
}
