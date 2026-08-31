package com.sasimilkservice.settlement.service;

import com.sasimilkservice.common.money.RoundingPolicy;

import java.math.BigDecimal;

/**
 * Strategy that turns {@code net_before_rounding} into {@code net_payable} and
 * reports the signed {@code round_off}. The concrete rule is configuration
 * ({@link RoundingPolicy} from {@code center}) and the exact production behaviour
 * is still open (OPEN_QUESTIONS #9), so settlement code depends on this interface
 * rather than a hard-coded rule.
 */
public interface RoundingRule {

    Rounded apply(BigDecimal netBeforeRounding);

    /**
     * @param netPayable  value to pay the producer
     * @param roundOff    netPayable - netBeforeRounding (signed; feeds settlement_items.round_off)
     */
    record Rounded(BigDecimal netPayable, BigDecimal roundOff) {}

    /** Default implementation backed by {@link RoundingPolicy}. */
    static RoundingRule fromPolicy(RoundingPolicy policy) {
        return netBeforeRounding -> new Rounded(
                policy.apply(netBeforeRounding),
                policy.roundOff(netBeforeRounding));
    }
}
