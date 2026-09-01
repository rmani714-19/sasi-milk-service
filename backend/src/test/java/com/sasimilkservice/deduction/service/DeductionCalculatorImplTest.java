package com.sasimilkservice.deduction.service;

import com.sasimilkservice.deduction.spi.DeductionCalculator.DeductionInput;
import com.sasimilkservice.deduction.spi.DeductionCalculator.DeductionResult;
import com.sasimilkservice.deduction.spi.DeductionCalculator.PendingDeduction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeductionCalculatorImplTest {

    private final DeductionCalculatorImpl calc = new DeductionCalculatorImpl();

    @Test
    void buckets_fixed_amounts_by_type() {
        DeductionResult r = calc.calculate(new DeductionInput(List.of(
                new PendingDeduction(1L, "FEED", new BigDecimal("120.00")),
                new PendingDeduction(2L, "FEED", new BigDecimal("80.00")),
                new PendingDeduction(3L, "VETERINARY", new BigDecimal("50.00")),
                new PendingDeduction(4L, "EMERGENCY", new BigDecimal("200.00")),
                new PendingDeduction(5L, "OTHER", new BigDecimal("30.00")))));

        assertThat(r.feedDeduction()).isEqualByComparingTo("200.00");
        assertThat(r.veterinaryDeduction()).isEqualByComparingTo("50.00");
        assertThat(r.otherDeduction()).isEqualByComparingTo("230.00"); // EMERGENCY + OTHER
        assertThat(r.totalDeduction()).isEqualByComparingTo("480.00");
        assertThat(r.lines()).hasSize(5);
    }

    @Test
    void empty_input_is_all_zero() {
        DeductionResult r = calc.calculate(new DeductionInput(List.of()));
        assertThat(r.totalDeduction()).isEqualByComparingTo("0.00");
        assertThat(r.lines()).isEmpty();
    }
}
