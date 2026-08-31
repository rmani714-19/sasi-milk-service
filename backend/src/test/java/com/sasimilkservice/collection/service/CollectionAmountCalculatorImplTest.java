package com.sasimilkservice.collection.service;

import com.sasimilkservice.collection.service.CollectionAmountCalculator.CollectionAmountInput;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.IncentiveResult;
import com.sasimilkservice.rate.spi.RateResolutionService.RateScope;
import com.sasimilkservice.rate.spi.RateResolutionService.ResolvedRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionAmountCalculatorImplTest {

    private final CollectionAmountCalculatorImpl calc = new CollectionAmountCalculatorImpl();

    @Test
    void base_is_litres_times_rate_net_adds_incentive() {
        ResolvedRate rate = new ResolvedRate(new BigDecimal("33.6550"), new BigDecimal("265"),
                RateScope.COMMON, 1L, false);
        IncentiveResult incentive = new IncentiveResult(
                new BigDecimal("1.0000"), BigDecimal.ZERO, new BigDecimal("1.0000"),
                new BigDecimal("10.00"), false, 7L, null);

        var b = calc.calculate(new CollectionAmountInput(new BigDecimal("10.00"), rate, incentive, BigDecimal.ZERO));

        assertThat(b.tsValueApplied()).isEqualByComparingTo("265");
        assertThat(b.applicableRate()).isEqualByComparingTo("33.6550");
        assertThat(b.baseAmount()).isEqualByComparingTo("336.55");
        assertThat(b.incentiveAmount()).isEqualByComparingTo("10.00");
        assertThat(b.deductionAmount()).isEqualByComparingTo("0.00");
        assertThat(b.netAmount()).isEqualByComparingTo("346.55");
    }

    @Test
    void point_of_collection_deduction_is_subtracted() {
        ResolvedRate rate = new ResolvedRate(new BigDecimal("30.0000"), new BigDecimal("250"),
                RateScope.REGION, 2L, false);
        IncentiveResult incentive = new IncentiveResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false, null, null);

        var b = calc.calculate(new CollectionAmountInput(new BigDecimal("5"), rate, incentive, new BigDecimal("12.50")));

        assertThat(b.baseAmount()).isEqualByComparingTo("150.00");
        assertThat(b.netAmount()).isEqualByComparingTo("137.50");
    }
}
