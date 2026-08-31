package com.sasimilkservice.collection.service;

import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.IncentiveResult;
import com.sasimilkservice.rate.spi.RateResolutionService.ResolvedRate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pure assembly of a single collection's money breakdown from an already-resolved
 * rate and incentive.
 */
@Component
public class CollectionAmountCalculatorImpl implements CollectionAmountCalculator {

    @Override
    public CollectionAmountBreakdown calculate(CollectionAmountInput input) {
        ResolvedRate rate = input.resolvedRate();
        IncentiveResult incentive = input.incentive();

        BigDecimal applicableRate = MoneyMath.rate(rate.ratePerLitre());
        BigDecimal base = MoneyMath.amount(input.litres(), applicableRate);
        BigDecimal incentiveAmount = MoneyMath.money(incentive.incentiveAmount());
        BigDecimal deduction = MoneyMath.money(MoneyMath.nz(input.pointOfCollectionDeduction()));
        BigDecimal net = MoneyMath.money(base.add(incentiveAmount).subtract(deduction));

        return new CollectionAmountBreakdown(
                MoneyMath.rate(rate.tsValue()),
                applicableRate,
                MoneyMath.rate(incentive.effectiveRatePerLitre()),
                base,
                incentiveAmount,
                deduction,
                net);
    }
}
