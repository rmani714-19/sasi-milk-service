package com.sasimilkservice.common.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class RoundingPolicyTest {

    @ParameterizedTest
    @CsvSource({
            // banker's rounding to whole rupee ("round as per banking")
            "336.55, 337.00",
            "336.49, 336.00",
            "2.50,   2.00",
            "3.50,   4.00",
            "10.00, 10.00"
    })
    void half_even_scale_zero(String input, String expected) {
        RoundingPolicy p = RoundingPolicy.of(RoundingMode.HALF_EVEN, 0);
        assertThat(p.apply(new java.math.BigDecimal(input))).isEqualByComparingTo(expected);
    }

    @Test
    void round_off_is_signed_difference() {
        RoundingPolicy p = RoundingPolicy.of(RoundingMode.HALF_EVEN, 0);
        assertThat(p.roundOff(new java.math.BigDecimal("336.55"))).isEqualByComparingTo("0.45");
        assertThat(p.roundOff(new java.math.BigDecimal("336.49"))).isEqualByComparingTo("-0.49");
    }

    @Test
    void none_policy_keeps_value_at_money_scale() {
        RoundingPolicy p = RoundingPolicy.none();
        assertThat(p.apply(new java.math.BigDecimal("336.55"))).isEqualByComparingTo("336.55");
        assertThat(p.roundOff(new java.math.BigDecimal("336.55"))).isEqualByComparingTo("0.00");
    }
}
