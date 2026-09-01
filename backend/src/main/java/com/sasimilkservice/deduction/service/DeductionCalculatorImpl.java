package com.sasimilkservice.deduction.service;

import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.deduction.spi.DeductionCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sums fixed-amount deductions and buckets them into feed / veterinary / other
 * for the settlement line. No percentage handling (client, OQ #6).
 */
@Component
public class DeductionCalculatorImpl implements DeductionCalculator {

    private static final String FEED = "FEED";
    private static final String VETERINARY = "VETERINARY";

    @Override
    public DeductionResult calculate(DeductionInput input) {
        BigDecimal feed = BigDecimal.ZERO;
        BigDecimal vet = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        List<AppliedDeduction> lines = new ArrayList<>();

        for (PendingDeduction d : input.pending()) {
            BigDecimal amount = MoneyMath.money(d.amount());
            lines.add(new AppliedDeduction(d.deductionTransactionId(), d.typeCode(), amount));
            switch (d.typeCode() == null ? "" : d.typeCode().toUpperCase()) {
                case FEED -> feed = feed.add(amount);
                case VETERINARY -> vet = vet.add(amount);
                default -> other = other.add(amount);
            }
        }

        return new DeductionResult(
                lines,
                MoneyMath.money(feed),
                MoneyMath.money(vet),
                MoneyMath.money(other),
                MoneyMath.sum(feed, vet, other));
    }
}
