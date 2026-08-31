package com.sasimilkservice.incentive.spi;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Computes the incentive for one collection. Published SPI of the {@code incentive}
 * module.
 *
 * <p>Two rate sources (spec incentives.types):
 * <ul>
 *   <li>COMMON — {@code incentive_rules} (optionally region-scoped), effective-dated.</li>
 *   <li>INDIVIDUAL — {@code producer_incentives}, effective-dated.</li>
 * </ul>
 *
 * <p>Combination (OQ #4). They are combined per {@link Stacking}
 * ({@code center.incentive_stacking}, default {@code ADD}) and then the result is
 * <strong>capped at {@code maxPerLitre}</strong> — the client's rule
 * "Max rs.2 ... by Admin" ({@code center.incentive_max_per_litre}):
 * <pre>
 *   stacked        = stacking == ADD ? common + individual
 *                                    : (individual &gt; 0 ? individual : common)
 *   effectiveRate  = min(stacked, maxPerLitre)
 *   incentiveAmount = round2(litres * effectiveRate)
 * </pre>
 * {@code effectiveRatePerLitre} is snapshotted onto
 * {@code milk_collections.incentive_rate_applied}.
 *
 * <p>Pure and deterministic.
 */
public interface IncentiveCalculator {

    IncentiveResult calculate(IncentiveQuery query);

    enum Stacking { ADD, REPLACE }

    /**
     * @param producerId     producer
     * @param regionId       producer's region at collection time (for region-scoped common rules)
     * @param collectionDate business date
     * @param litres         collected quantity
     * @param stacking       {@code center.incentive_stacking}
     * @param maxPerLitre    {@code center.incentive_max_per_litre} (hard cap; e.g. 2.00)
     */
    record IncentiveQuery(
            long producerId,
            Long regionId,
            LocalDate collectionDate,
            BigDecimal litres,
            Stacking stacking,
            BigDecimal maxPerLitre) {
    }

    /**
     * @param commonRatePerLitre     resolved COMMON rate (0 if none)
     * @param individualRatePerLitre resolved INDIVIDUAL rate (0 if none)
     * @param effectiveRatePerLitre  after {@link Stacking} and the {@code maxPerLitre} cap
     * @param incentiveAmount        round2(litres * effectiveRatePerLitre)
     * @param cappedByMax            true when the cap actually reduced the stacked rate
     * @param commonRuleId           source rule id for the snapshot (nullable)
     * @param individualRuleId       source producer_incentive id for the snapshot (nullable)
     */
    record IncentiveResult(
            BigDecimal commonRatePerLitre,
            BigDecimal individualRatePerLitre,
            BigDecimal effectiveRatePerLitre,
            BigDecimal incentiveAmount,
            boolean cappedByMax,
            Long commonRuleId,
            Long individualRuleId) {
    }
}
