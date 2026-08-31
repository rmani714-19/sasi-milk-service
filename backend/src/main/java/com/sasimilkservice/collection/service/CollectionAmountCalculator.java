package com.sasimilkservice.collection.service;

import com.sasimilkservice.rate.spi.RateResolutionService.ResolvedRate;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.IncentiveResult;

import java.math.BigDecimal;

/**
 * Assembles the money breakdown for a single milk collection from an already
 * resolved rate and incentive. Pure and deterministic.
 *
 * <pre>
 *   base_amount      = round2(litres * applicable_rate)
 *   incentive_amount = incentiveResult.incentiveAmount
 *   deduction_amount = point-of-collection deductions (normally 0; reserved)
 *   net_amount       = base_amount + incentive_amount - deduction_amount
 * </pre>
 *
 * The result's snapshot fields are persisted verbatim onto {@code milk_collections}
 * and are never recomputed when rates/incentives later change
 * (spec milk_collection.rules).
 */
public interface CollectionAmountCalculator {

    CollectionAmountBreakdown calculate(CollectionAmountInput input);

    /**
     * @param litres               validated quantity (&gt; 0)
     * @param resolvedRate         from RateResolutionService
     * @param incentive            from IncentiveCalculator
     * @param pointOfCollectionDeduction usually {@link BigDecimal#ZERO}; reserved for
     *                             configs that deduct at collection time
     */
    record CollectionAmountInput(
            BigDecimal litres,
            ResolvedRate resolvedRate,
            IncentiveResult incentive,
            BigDecimal pointOfCollectionDeduction) {
    }

    /**
     * Mirrors the persisted columns on {@code milk_collections}.
     *
     * @param tsValueApplied        formula coefficient used (scale 4) — snapshot
     * @param applicableRate        {@code (fat + snf) * tsValueApplied / 100} (scale 4) — snapshot
     * @param incentiveRateApplied  snapshot (scale 4)
     * @param baseAmount            round2(litres * applicableRate)
     * @param incentiveAmount       money scale
     * @param deductionAmount       money scale
     * @param netAmount             base + incentive - deduction
     */
    record CollectionAmountBreakdown(
            BigDecimal tsValueApplied,
            BigDecimal applicableRate,
            BigDecimal incentiveRateApplied,
            BigDecimal baseAmount,
            BigDecimal incentiveAmount,
            BigDecimal deductionAmount,
            BigDecimal netAmount) {
    }
}
