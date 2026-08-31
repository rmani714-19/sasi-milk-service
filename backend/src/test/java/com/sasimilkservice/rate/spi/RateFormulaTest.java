package com.sasimilkservice.rate.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RateFormulaTest {

    @Test
    void computes_rate_as_fat_plus_snf_times_ts_over_100() {
        // (4.1 + 8.6) * 265 / 100 = 33.6550
        BigDecimal rate = RateFormula.ratePerLitre(
                new BigDecimal("4.1"), new BigDecimal("8.6"), new BigDecimal("265"));
        assertThat(rate).isEqualByComparingTo("33.6550");
    }

    @ParameterizedTest
    @CsvSource({
            "3.5, 8.5, 260, 31.2000",
            "4.0, 9.0, 300, 39.0000",
            "5.0, 8.0, 250, 32.5000",
            "0.0, 0.0, 265, 0.0000"
    })
    void formula_table(String fat, String snf, String ts, String expected) {
        assertThat(RateFormula.ratePerLitre(new BigDecimal(fat), new BigDecimal(snf), new BigDecimal(ts)))
                .isEqualByComparingTo(expected);
    }

    @Test
    void null_inputs_are_treated_as_zero() {
        assertThat(RateFormula.ratePerLitre(null, null, null)).isEqualByComparingTo("0");
    }
}
