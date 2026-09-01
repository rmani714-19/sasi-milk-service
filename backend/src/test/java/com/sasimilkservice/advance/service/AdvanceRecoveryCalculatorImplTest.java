package com.sasimilkservice.advance.service;

import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AccountRecovery;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceAccountState;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceRecoveryInput;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceRecoveryResult;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.RecoveryMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdvanceRecoveryCalculatorImplTest {

    private final AdvanceRecoveryCalculatorImpl calc = new AdvanceRecoveryCalculatorImpl();

    private static AdvanceAccountState fixed(long id, String outstanding, String perWeek) {
        return new AdvanceAccountState(id, new BigDecimal(outstanding), RecoveryMode.FIXED_PER_WEEK,
                new BigDecimal(perWeek), null);
    }

    @Test
    void client_example_10k_advance_500_per_week_1500_net() {
        // OQ #7: earns 1500, weekly recovery 500 -> pay 1000, recover 500, outstanding 9500
        AdvanceRecoveryResult r = calc.calculate(new AdvanceRecoveryInput(
                List.of(fixed(1L, "10000.00", "500.00")), new BigDecimal("1500.00")));

        assertThat(r.totalRecovery()).isEqualByComparingTo("500.00");
        AccountRecovery a = r.recoveries().get(0);
        assertThat(a.recoveredAmount()).isEqualByComparingTo("500.00");
        assertThat(a.balanceAfter()).isEqualByComparingTo("9500.00");
        assertThat(a.closesAccount()).isFalse();
    }

    @Test
    void recovery_is_capped_at_net_when_net_below_weekly_amount() {
        AdvanceRecoveryResult r = calc.calculate(new AdvanceRecoveryInput(
                List.of(fixed(1L, "10000.00", "500.00")), new BigDecimal("300.00")));
        assertThat(r.totalRecovery()).isEqualByComparingTo("300.00");
        assertThat(r.recoveries().get(0).balanceAfter()).isEqualByComparingTo("9700.00");
    }

    @Test
    void recovery_is_capped_at_outstanding_and_closes_account() {
        AdvanceRecoveryResult r = calc.calculate(new AdvanceRecoveryInput(
                List.of(fixed(1L, "400.00", "500.00")), new BigDecimal("1500.00")));
        assertThat(r.totalRecovery()).isEqualByComparingTo("400.00");
        assertThat(r.recoveries().get(0).balanceAfter()).isEqualByComparingTo("0.00");
        assertThat(r.recoveries().get(0).closesAccount()).isTrue();
    }

    @Test
    void negative_net_recovers_nothing() {
        AdvanceRecoveryResult r = calc.calculate(new AdvanceRecoveryInput(
                List.of(fixed(1L, "10000.00", "500.00")), new BigDecimal("-50.00")));
        assertThat(r.totalRecovery()).isEqualByComparingTo("0.00");
        assertThat(r.recoveries().get(0).balanceAfter()).isEqualByComparingTo("10000.00");
    }

    @Test
    void multiple_accounts_recovered_oldest_first_until_net_exhausted() {
        AdvanceRecoveryResult r = calc.calculate(new AdvanceRecoveryInput(
                List.of(fixed(1L, "10000.00", "500.00"), fixed(2L, "10000.00", "500.00")),
                new BigDecimal("700.00")));
        assertThat(r.totalRecovery()).isEqualByComparingTo("700.00");
        assertThat(r.recoveries().get(0).recoveredAmount()).isEqualByComparingTo("500.00");
        assertThat(r.recoveries().get(1).recoveredAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void percentage_of_net_mode() {
        AdvanceAccountState pct = new AdvanceAccountState(1L, new BigDecimal("10000.00"),
                RecoveryMode.PERCENTAGE_OF_NET, null, new BigDecimal("10.000"));
        AdvanceRecoveryResult r = calc.calculate(new AdvanceRecoveryInput(
                List.of(pct), new BigDecimal("1500.00")));
        assertThat(r.totalRecovery()).isEqualByComparingTo("150.00");
    }
}
