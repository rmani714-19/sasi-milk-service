package com.sasimilkservice.advance.service;

import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator;
import com.sasimilkservice.common.money.MoneyMath;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure weekly advance-recovery math (client, OQ #7):
 * <pre>
 *   base       = max(netAvailable, 0)
 *   candidate  = FIXED_PER_WEEK    -> recovery_amount
 *                PERCENTAGE_OF_NET -> base * recovery_percentage / 100
 *   recovered  = min(candidate, outstanding, remaining net)
 * </pre>
 * Applied oldest account first; {@code remaining} is drawn down as it goes so the
 * producer's net can never be driven below zero. Outstanding never goes negative.
 */
@Component
public class AdvanceRecoveryCalculatorImpl implements AdvanceRecoveryCalculator {

    @Override
    public AdvanceRecoveryResult calculate(AdvanceRecoveryInput input) {
        BigDecimal base = MoneyMath.atLeastZero(input.netAvailable());
        BigDecimal remaining = base;
        BigDecimal total = BigDecimal.ZERO;
        List<AccountRecovery> recoveries = new ArrayList<>();

        for (AdvanceAccountState acct : input.accounts()) {
            BigDecimal outstanding = MoneyMath.money(acct.outstandingAmount());
            BigDecimal candidate = switch (acct.mode()) {
                case FIXED_PER_WEEK -> MoneyMath.money(acct.recoveryAmount());
                case PERCENTAGE_OF_NET -> MoneyMath.percentOf(base, acct.recoveryPercentage());
            };

            BigDecimal recovered = candidate.min(outstanding).min(remaining);
            recovered = MoneyMath.atLeastZero(recovered);

            BigDecimal balanceAfter = MoneyMath.money(outstanding.subtract(recovered));
            recoveries.add(new AccountRecovery(acct.advanceAccountId(), recovered, balanceAfter,
                    balanceAfter.signum() == 0));

            remaining = MoneyMath.money(remaining.subtract(recovered));
            total = total.add(recovered);
        }

        return new AdvanceRecoveryResult(recoveries, MoneyMath.money(total));
    }
}
