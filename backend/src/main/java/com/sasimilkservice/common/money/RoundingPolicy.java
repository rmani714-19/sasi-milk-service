package com.sasimilkservice.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding configuration for a settlement, sourced from {@code center.rounding_mode}
 * / {@code center.rounding_scale} and snapshotted onto every {@code settlements} row
 * so a settlement can be reproduced exactly (spec: "Every settlement calculation
 * must be reproducible").
 *
 * <p>{@code mode == null} means "no rounding" ({@code center.rounding_mode = 'NONE'}):
 * the value is returned unchanged apart from being normalised to {@link #MONEY_SCALE}.
 *
 * <p>The exact production rule is still an open question (OPEN_QUESTIONS #9); the
 * default seeded config is {@code HALF_UP} at scale {@code 0} (whole rupee).
 */
public record RoundingPolicy(RoundingMode mode, int scale) {

    /** Scale every monetary amount is stored/compared at. */
    public static final int MONEY_SCALE = 2;

    public static RoundingPolicy none() {
        return new RoundingPolicy(null, MONEY_SCALE);
    }

    public static RoundingPolicy of(RoundingMode mode, int scale) {
        return new RoundingPolicy(mode, scale);
    }

    /**
     * @return the amount rounded to {@link #scale()} using {@link #mode()}, then
     *         re-scaled to {@link #MONEY_SCALE} for storage. Never returns null.
     */
    public BigDecimal apply(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal rounded = mode == null ? value : value.setScale(scale, mode);
        return rounded.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    /** The signed round-off applied: {@code apply(amount) - amount}. */
    public BigDecimal roundOff(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return apply(amount).subtract(value);
    }
}
