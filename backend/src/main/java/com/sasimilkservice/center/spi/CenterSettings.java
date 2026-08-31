package com.sasimilkservice.center.spi;

import com.sasimilkservice.common.money.RoundingPolicy;
import com.sasimilkservice.incentive.spi.IncentiveCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable snapshot of the center's configurable business rules, published for
 * other modules (collection, settlement). Built by {@code CenterService}.
 */
public record CenterSettings(
        String currencyCode,
        RoundingMode roundingMode,
        int roundingScale,
        IncentiveCalculator.Stacking incentiveStacking,
        BigDecimal incentiveMaxPerLitre,
        boolean allowMissingRate,
        BigDecimal fatMin,
        BigDecimal fatMax,
        BigDecimal snfMin,
        BigDecimal snfMax) {

    /** @return the rounding policy for settlement final-payable ("round as per banking"). */
    public RoundingPolicy roundingPolicy() {
        return roundingMode == null
                ? RoundingPolicy.none()
                : RoundingPolicy.of(roundingMode, roundingScale);
    }

    public boolean fatInRange(BigDecimal fat) {
        return fat != null && fat.compareTo(fatMin) >= 0 && fat.compareTo(fatMax) <= 0;
    }

    public boolean snfInRange(BigDecimal snf) {
        return snf != null && snf.compareTo(snfMin) >= 0 && snf.compareTo(snfMax) <= 0;
    }
}
