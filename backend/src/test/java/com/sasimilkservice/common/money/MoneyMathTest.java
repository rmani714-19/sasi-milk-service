package com.sasimilkservice.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMathTest {

    @Test
    void amount_is_litres_times_rate_at_money_scale() {
        assertThat(MoneyMath.amount(new BigDecimal("10.00"), new BigDecimal("33.6550")))
                .isEqualByComparingTo("336.55");
    }

    @Test
    void amount_rounds_half_up_to_two_dp() {
        assertThat(MoneyMath.amount(new BigDecimal("3"), new BigDecimal("1.005")))
                .isEqualByComparingTo("3.02");
    }

    @Test
    void percent_of_base() {
        assertThat(MoneyMath.percentOf(new BigDecimal("1000"), new BigDecimal("2.5")))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void sum_treats_nulls_as_zero() {
        assertThat(MoneyMath.sum(new BigDecimal("10"), null, new BigDecimal("5.25")))
                .isEqualByComparingTo("15.25");
    }

    @Test
    void atLeastZero_floors_negatives() {
        assertThat(MoneyMath.atLeastZero(new BigDecimal("-3.10"))).isEqualByComparingTo("0.00");
        assertThat(MoneyMath.atLeastZero(new BigDecimal("3.10"))).isEqualByComparingTo("3.10");
    }
}
