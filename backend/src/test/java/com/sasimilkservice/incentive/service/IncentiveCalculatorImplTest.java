package com.sasimilkservice.incentive.service;

import com.sasimilkservice.incentive.domain.IncentiveRule;
import com.sasimilkservice.incentive.domain.ProducerIncentive;
import com.sasimilkservice.incentive.repository.IncentiveRuleRepository;
import com.sasimilkservice.incentive.repository.ProducerIncentiveRepository;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.IncentiveQuery;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.IncentiveResult;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.Stacking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncentiveCalculatorImplTest {

    private IncentiveRuleRepository commonRules;
    private ProducerIncentiveRepository individualRules;
    private IncentiveCalculatorImpl calc;

    private static final LocalDate DAY = LocalDate.of(2026, 8, 31);
    private static final BigDecimal CAP = new BigDecimal("2.00");
    private static final long PRODUCER = 1L;
    private static final long REGION = 3L;

    @BeforeEach
    void setUp() {
        commonRules = mock(IncentiveRuleRepository.class);
        individualRules = mock(ProducerIncentiveRepository.class);
        calc = new IncentiveCalculatorImpl(commonRules, individualRules);
        when(commonRules.findActiveCovering(any(), any(), any())).thenReturn(List.of());
        when(individualRules.findActiveCovering(any(), any())).thenReturn(List.of());
    }

    private IncentiveQuery query(String litres, Stacking stacking) {
        return new IncentiveQuery(PRODUCER, REGION, DAY, new BigDecimal(litres), stacking, CAP);
    }

    private void commonRate(String rate) {
        IncentiveRule r = new IncentiveRule();
        r.setIncentiveRatePerLitre(new BigDecimal(rate));
        when(commonRules.findActiveCovering(IncentiveRule.Scope.COMMON, null, DAY)).thenReturn(List.of(r));
    }

    private void regionRate(String rate) {
        IncentiveRule r = new IncentiveRule();
        r.setScope(IncentiveRule.Scope.REGION);
        r.setRegionId(REGION);
        r.setIncentiveRatePerLitre(new BigDecimal(rate));
        when(commonRules.findActiveCovering(IncentiveRule.Scope.REGION, REGION, DAY)).thenReturn(List.of(r));
    }

    private void individualRate(String rate) {
        ProducerIncentive p = new ProducerIncentive();
        p.setIncentiveRatePerLitre(new BigDecimal(rate));
        when(individualRules.findActiveCovering(PRODUCER, DAY)).thenReturn(List.of(p));
    }

    @Test
    void add_stacking_sums_common_and_individual() {
        commonRate("0.75");
        individualRate("0.50");
        IncentiveResult r = calc.calculate(query("10", Stacking.ADD));
        assertThat(r.effectiveRatePerLitre()).isEqualByComparingTo("1.25");
        assertThat(r.incentiveAmount()).isEqualByComparingTo("12.50");
        assertThat(r.cappedByMax()).isFalse();
    }

    @Test
    void total_is_capped_at_two_rupees() {
        commonRate("1.50");
        individualRate("1.25"); // stacked 2.75 -> capped to 2.00
        IncentiveResult r = calc.calculate(query("10", Stacking.ADD));
        assertThat(r.effectiveRatePerLitre()).isEqualByComparingTo("2.00");
        assertThat(r.incentiveAmount()).isEqualByComparingTo("20.00");
        assertThat(r.cappedByMax()).isTrue();
    }

    @Test
    void replace_stacking_prefers_individual_when_present() {
        commonRate("1.50");
        individualRate("0.80");
        IncentiveResult r = calc.calculate(query("10", Stacking.REPLACE));
        assertThat(r.effectiveRatePerLitre()).isEqualByComparingTo("0.80");
    }

    @Test
    void replace_stacking_falls_back_to_common_when_no_individual() {
        commonRate("1.50");
        IncentiveResult r = calc.calculate(query("10", Stacking.REPLACE));
        assertThat(r.effectiveRatePerLitre()).isEqualByComparingTo("1.50");
    }

    @Test
    void no_rules_means_zero_incentive() {
        IncentiveResult r = calc.calculate(query("10", Stacking.ADD));
        assertThat(r.effectiveRatePerLitre()).isEqualByComparingTo("0");
        assertThat(r.incentiveAmount()).isEqualByComparingTo("0");
    }

    @Test
    void region_scoped_common_rule_wins_over_plain_common() {
        regionRate("1.10");
        commonRate("0.40");
        IncentiveResult r = calc.calculate(query("10", Stacking.ADD));
        assertThat(r.commonRatePerLitre()).isEqualByComparingTo("1.10");
    }
}
