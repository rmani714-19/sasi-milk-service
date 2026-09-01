package com.sasimilkservice.settlement.service;

import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceRecoveryResult;
import com.sasimilkservice.common.money.RoundingPolicy;
import com.sasimilkservice.deduction.spi.DeductionCalculator.DeductionResult;
import com.sasimilkservice.settlement.service.SettlementCalculator.CollectionContribution;
import com.sasimilkservice.settlement.service.SettlementCalculator.SettlementLineInput;
import com.sasimilkservice.settlement.service.SettlementCalculator.SettlementLineResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementCalculatorImplTest {

    private final SettlementCalculatorImpl calc = new SettlementCalculatorImpl();

    private static final RoundingRule BANKING =
            RoundingRule.fromPolicy(RoundingPolicy.of(RoundingMode.HALF_EVEN, 0));

    private static DeductionResult deductions(String feed, String vet, String other) {
        BigDecimal f = new BigDecimal(feed), v = new BigDecimal(vet), o = new BigDecimal(other);
        return new DeductionResult(List.of(), f, v, o, f.add(v).add(o));
    }

    private static AdvanceRecoveryResult advance(String total) {
        return new AdvanceRecoveryResult(List.of(), new BigDecimal(total));
    }

    @Test
    void full_weekly_line_with_deductions_advance_and_banking_rounding() {
        List<CollectionContribution> collections = List.of(
                new CollectionContribution(1L, "MORNING", new BigDecimal("100.00"),
                        new BigDecimal("3365.50"), new BigDecimal("100.00")),
                new CollectionContribution(2L, "EVENING", new BigDecimal("80.00"),
                        new BigDecimal("2692.40"), new BigDecimal("80.00")));

        SettlementLineResult r = calc.calculateLine(new SettlementLineInput(
                7L, 1L, collections,
                deductions("200.00", "50.00", "0.00"),
                advance("500.00"),
                BigDecimal.ZERO,
                BANKING));

        assertThat(r.morningLitres()).isEqualByComparingTo("100.00");
        assertThat(r.eveningLitres()).isEqualByComparingTo("80.00");
        assertThat(r.totalLitres()).isEqualByComparingTo("180.00");
        assertThat(r.collectionCount()).isEqualTo(2);
        assertThat(r.milkAmount()).isEqualByComparingTo("6057.90");
        assertThat(r.incentiveAmount()).isEqualByComparingTo("180.00");
        assertThat(r.grossAmount()).isEqualByComparingTo("6237.90");
        assertThat(r.feedDeduction()).isEqualByComparingTo("200.00");
        assertThat(r.veterinaryDeduction()).isEqualByComparingTo("50.00");
        assertThat(r.otherDeduction()).isEqualByComparingTo("0.00");
        assertThat(r.advanceRecovery()).isEqualByComparingTo("500.00");
        assertThat(r.totalDeduction()).isEqualByComparingTo("750.00");
        assertThat(r.netBeforeRounding()).isEqualByComparingTo("5487.90");
        assertThat(r.roundOff()).isEqualByComparingTo("0.10");
        assertThat(r.netPayable()).isEqualByComparingTo("5488.00");
        // litre-weighted average: 6057.90 / 180
        assertThat(r.averageRate()).isEqualByComparingTo("33.6550");
    }

    @Test
    void adjustment_is_applied_signed() {
        List<CollectionContribution> collections = List.of(
                new CollectionContribution(1L, "MORNING", new BigDecimal("10.00"),
                        new BigDecimal("336.55"), BigDecimal.ZERO));

        SettlementLineResult credit = calc.calculateLine(new SettlementLineInput(
                7L, null, collections, deductions("0", "0", "0"), advance("0"),
                new BigDecimal("13.45"), BANKING));
        assertThat(credit.netBeforeRounding()).isEqualByComparingTo("350.00");
        assertThat(credit.netPayable()).isEqualByComparingTo("350.00");

        SettlementLineResult debit = calc.calculateLine(new SettlementLineInput(
                7L, null, collections, deductions("0", "0", "0"), advance("0"),
                new BigDecimal("-36.55"), BANKING));
        assertThat(debit.netBeforeRounding()).isEqualByComparingTo("300.00");
    }

    @Test
    void no_collections_yields_zero_line() {
        SettlementLineResult r = calc.calculateLine(new SettlementLineInput(
                7L, null, List.of(), deductions("0", "0", "0"), advance("0"),
                BigDecimal.ZERO, BANKING));
        assertThat(r.totalLitres()).isEqualByComparingTo("0.00");
        assertThat(r.averageRate()).isEqualByComparingTo("0");
        assertThat(r.netPayable()).isEqualByComparingTo("0.00");
    }
}
