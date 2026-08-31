package com.sasimilkservice.settlement.service;

import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceRecoveryResult;
import com.sasimilkservice.deduction.spi.DeductionCalculator.DeductionResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure weekly-settlement math for one producer (one {@code settlement_items} row).
 * Orchestration — loading collections, calling
 * {@link com.sasimilkservice.deduction.spi.DeductionCalculator} and
 * {@link com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator}, persisting,
 * freezing on approval — lives in the settlement application service.
 *
 * <pre>
 *   total_litres        = morning_litres + evening_litres
 *   milk_amount         = Σ collection.base_amount           (rate snapshots as collected)
 *   incentive_amount    = Σ collection.incentive_amount
 *   gross_amount        = milk_amount + incentive_amount
 *   total_deduction     = feed + veterinary + other + advance_recovery
 *   net_before_rounding = gross_amount - total_deduction + adjustment_amount
 *   (net_payable, round_off) = roundingRule.apply(net_before_rounding)
 * </pre>
 *
 * Rules enforced (spec settlement.rules + financial_accuracy):
 * <ul>
 *   <li>Never re-price historical collections — inputs carry the amounts recorded
 *       at collection time.</li>
 *   <li>{@code average_rate} is the litre-weighted mean (OPEN_QUESTIONS #18).</li>
 *   <li>Advance recovery comes from the advance ledger, not generic deductions.</li>
 *   <li>Every output field is echoed into {@code calculation_snapshot} for
 *       reproducibility; {@link #engineVersion()} is stored on the settlement.</li>
 * </ul>
 */
public interface SettlementCalculator {

    /** Bump whenever the formula changes; stored in {@code settlements.calc_engine_version}. */
    String CALC_ENGINE_VERSION = "1.0.0";

    default String engineVersion() {
        return CALC_ENGINE_VERSION;
    }

    SettlementLineResult calculateLine(SettlementLineInput input);

    /**
     * @param producerId       producer
     * @param regionId         producer region at settlement time (snapshot)
     * @param collections      per-collection contributions in the period
     * @param deductions       resolved non-advance deductions (from DeductionCalculator)
     * @param advanceRecovery  resolved advance recovery (from AdvanceRecoveryCalculator)
     * @param adjustmentAmount signed net of ACTIVE settlement_adjustments (+credit / -debit)
     * @param roundingRule     from center config, snapshotted on the settlement
     */
    record SettlementLineInput(
            long producerId,
            Long regionId,
            List<CollectionContribution> collections,
            DeductionResult deductions,
            AdvanceRecoveryResult advanceRecovery,
            BigDecimal adjustmentAmount,
            RoundingRule roundingRule) {
    }

    /**
     * One collection's contribution, using amounts recorded at collection time.
     *
     * @param collectionId   source row
     * @param shift          MORNING / EVENING (drives morning/evening litre split)
     * @param litres         milk_quantity_litres
     * @param baseAmount     milk_collections.base_amount
     * @param incentiveAmount milk_collections.incentive_amount
     */
    record CollectionContribution(
            long collectionId,
            String shift,
            BigDecimal litres,
            BigDecimal baseAmount,
            BigDecimal incentiveAmount) {
    }

    /** Mirrors the persisted columns on {@code settlement_items}. */
    record SettlementLineResult(
            BigDecimal morningLitres,
            BigDecimal eveningLitres,
            BigDecimal totalLitres,
            int collectionCount,
            BigDecimal averageRate,
            BigDecimal milkAmount,
            BigDecimal incentiveAmount,
            BigDecimal grossAmount,
            BigDecimal feedDeduction,
            BigDecimal veterinaryDeduction,
            BigDecimal otherDeduction,
            BigDecimal advanceRecovery,
            BigDecimal totalDeduction,
            BigDecimal adjustmentAmount,
            BigDecimal netBeforeRounding,
            BigDecimal roundOff,
            BigDecimal netPayable) {
    }
}
