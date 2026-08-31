package com.sasimilkservice.rate.spi;

import com.sasimilkservice.common.money.MoneyMath;

import java.math.BigDecimal;

/**
 * The milk-rate formula, confirmed by the client (OPEN_QUESTIONS #1/#3):
 *
 * <pre>
 *     rate_per_litre = (fat_percentage + snf_percentage) * ts_value / 100
 * </pre>
 *
 * where {@code ts_value} is an effective-dated coefficient held on
 * {@code rate_charts} (a COMMON value, with an optional per-REGION override).
 * There is no FAT/SNF rate matrix.
 *
 * <p>Pure function; result carried at {@link MoneyMath#RATE_SCALE} (4 dp) and
 * snapshotted onto {@code milk_collections.applicable_rate}.
 */
public final class RateFormula {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RateFormula() {}

    public static BigDecimal ratePerLitre(BigDecimal fatPercentage,
                                          BigDecimal snfPercentage,
                                          BigDecimal tsValue) {
        BigDecimal raw = MoneyMath.nz(fatPercentage)
                .add(MoneyMath.nz(snfPercentage))
                .multiply(MoneyMath.nz(tsValue));
        return MoneyMath.rate(raw.divide(HUNDRED, MoneyMath.RATE_SCALE + 4, java.math.RoundingMode.HALF_UP));
    }
}
