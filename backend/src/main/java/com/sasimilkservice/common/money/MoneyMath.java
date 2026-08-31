package com.sasimilkservice.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central helpers for money and quantity arithmetic. Every monetary value in the
 * system is a {@link BigDecimal}; {@code double}/{@code float} are never used for
 * money or milk quantity (spec: financial_accuracy).
 *
 * <p>Convention:
 * <ul>
 *   <li>Quantities (litres) carry scale {@value #QUANTITY_SCALE}.</li>
 *   <li>Rates (per litre) carry scale {@value #RATE_SCALE}.</li>
 *   <li>Line amounts are rounded to {@value RoundingPolicy#MONEY_SCALE} with
 *       {@link RoundingMode#HALF_UP} <em>only</em> at the point a value becomes a
 *       stored line amount. Intermediate products keep full precision.</li>
 * </ul>
 */
public final class MoneyMath {

    public static final int QUANTITY_SCALE = 2;
    public static final int RATE_SCALE = 4;

    private MoneyMath() {}

    /** litres * ratePerLitre, rounded to money scale (HALF_UP). */
    public static BigDecimal amount(BigDecimal litres, BigDecimal ratePerLitre) {
        return money(nz(litres).multiply(nz(ratePerLitre)));
    }

    /** base * percentage / 100, rounded to money scale (HALF_UP). */
    public static BigDecimal percentOf(BigDecimal base, BigDecimal percentage) {
        return money(nz(base).multiply(nz(percentage))
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
    }

    public static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(RoundingPolicy.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal quantity(BigDecimal v) {
        return nz(v).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal rate(BigDecimal v) {
        return nz(v).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Sum, treating nulls as zero; result at money scale. */
    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            total = total.add(nz(v));
        }
        return money(total);
    }

    /** max(value, 0). */
    public static BigDecimal atLeastZero(BigDecimal value) {
        return nz(value).signum() < 0 ? BigDecimal.ZERO.setScale(RoundingPolicy.MONEY_SCALE) : money(value);
    }

    /** min of two non-null-safe values, at money scale. */
    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return money(nz(a).min(nz(b)));
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
