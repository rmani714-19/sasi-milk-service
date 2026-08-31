package com.sasimilkservice.rate.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves the milk rate per litre for a single collection. Published SPI of the
 * {@code rate} module — the {@code collection} and {@code settlement} modules call
 * this and never touch rate tables directly.
 *
 * <p>Rate is a formula, not a lookup ({@link RateFormula}):
 * {@code rate = (fat + snf) * ts_value / 100}. Resolution therefore only has to
 * pick the right {@code ts_value} (spec regions.default_rate_priority, OQ #3):
 * <ol>
 *   <li>An {@code ACTIVE} REGION {@code rate_charts} row covering {@code collectionDate}
 *       for the producer's region → use its {@code ts_value}.</li>
 *   <li>Else an {@code ACTIVE} COMMON row covering the date → use its {@code ts_value}.</li>
 *   <li>Else fail — never silently price at zero (spec rate_management.validation)
 *       unless {@code allowMissingRate}.</li>
 * </ol>
 *
 * <p>Deterministic for a given {@code (query, rate_charts data)}; no persistence
 * side effects.
 */
public interface RateResolutionService {

    /**
     * @return the resolved rate; every field is copied verbatim onto
     *         {@code milk_collections} and never recomputed later.
     * @throws RateNotConfiguredException when no chart covers the date and
     *         {@code allowMissingRate} is false.
     */
    ResolvedRate resolve(ResolveRateQuery query);

    /** Non-throwing variant for previews / dashboards. */
    Optional<ResolvedRate> tryResolve(ResolveRateQuery query);

    enum RateScope { COMMON, REGION }

    /**
     * @param regionId         producer's region at collection time; null =&gt; only COMMON considered
     * @param collectionDate   business date of the collection
     * @param fatPercentage    measured FAT
     * @param snfPercentage    measured SNF
     * @param allowMissingRate {@code center.allow_missing_rate}
     */
    record ResolveRateQuery(
            Long regionId,
            LocalDate collectionDate,
            BigDecimal fatPercentage,
            BigDecimal snfPercentage,
            boolean allowMissingRate) {
    }

    /**
     * @param ratePerLitre  {@code (fat + snf) * tsValue / 100}, scale 4
     * @param tsValue       coefficient used — snapshot → {@code milk_collections.ts_value_applied}
     * @param scope         which chart won
     * @param rateChartId   snapshot → {@code milk_collections.rate_chart_id}
     * @param missing       true when no chart matched but {@code allowMissingRate} let it
     *                      through (tsValue = 0, ratePerLitre = 0)
     */
    record ResolvedRate(
            BigDecimal ratePerLitre,
            BigDecimal tsValue,
            RateScope scope,
            Long rateChartId,
            boolean missing) {
    }
}
