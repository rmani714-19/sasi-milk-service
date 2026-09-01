package com.sasimilkservice.settlement.service;

import com.sasimilkservice.common.money.MoneyMath;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure per-producer weekly-settlement math (see {@link SettlementCalculator}).
 *
 * <pre>
 *   gross               = Σ base_amount + Σ incentive_amount
 *   total_deduction     = feed + veterinary + other + advance_recovery
 *   net_before_rounding = gross - total_deduction + adjustment
 *   (net_payable, round_off) = roundingRule.apply(net_before_rounding)
 *   average_rate        = milk_amount / total_litres (litre-weighted; 0 when no litres)
 * </pre>
 */
@Component
public class SettlementCalculatorImpl implements SettlementCalculator {

    @Override
    public SettlementLineResult calculateLine(SettlementLineInput in) {
        BigDecimal morning = BigDecimal.ZERO;
        BigDecimal evening = BigDecimal.ZERO;
        BigDecimal milkAmount = BigDecimal.ZERO;
        BigDecimal incentiveAmount = BigDecimal.ZERO;

        for (CollectionContribution c : in.collections()) {
            BigDecimal litres = MoneyMath.nz(c.litres());
            if ("EVENING".equalsIgnoreCase(c.shift())) {
                evening = evening.add(litres);
            } else {
                morning = morning.add(litres);
            }
            milkAmount = milkAmount.add(MoneyMath.nz(c.baseAmount()));
            incentiveAmount = incentiveAmount.add(MoneyMath.nz(c.incentiveAmount()));
        }

        BigDecimal totalLitres = MoneyMath.quantity(morning.add(evening));
        milkAmount = MoneyMath.money(milkAmount);
        incentiveAmount = MoneyMath.money(incentiveAmount);
        BigDecimal gross = MoneyMath.sum(milkAmount, incentiveAmount);

        BigDecimal feed = MoneyMath.money(in.deductions().feedDeduction());
        BigDecimal vet = MoneyMath.money(in.deductions().veterinaryDeduction());
        BigDecimal other = MoneyMath.money(in.deductions().otherDeduction());
        BigDecimal advanceRecovery = MoneyMath.money(in.advanceRecovery().totalRecovery());
        BigDecimal totalDeduction = MoneyMath.sum(feed, vet, other, advanceRecovery);

        BigDecimal adjustment = MoneyMath.money(MoneyMath.nz(in.adjustmentAmount()));
        BigDecimal netBeforeRounding = MoneyMath.money(gross.subtract(totalDeduction).add(adjustment));

        RoundingRule.Rounded rounded = in.roundingRule().apply(netBeforeRounding);

        BigDecimal averageRate = totalLitres.signum() == 0
                ? BigDecimal.ZERO
                : milkAmount.divide(totalLitres, MoneyMath.RATE_SCALE, RoundingMode.HALF_UP);

        return new SettlementLineResult(
                MoneyMath.quantity(morning),
                MoneyMath.quantity(evening),
                totalLitres,
                in.collections().size(),
                MoneyMath.rate(averageRate),
                milkAmount,
                incentiveAmount,
                gross,
                feed,
                vet,
                other,
                advanceRecovery,
                totalDeduction,
                adjustment,
                netBeforeRounding,
                MoneyMath.money(rounded.roundOff()),
                MoneyMath.money(rounded.netPayable()));
    }
}
