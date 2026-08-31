package com.sasimilkservice.incentive.service;

import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.incentive.domain.IncentiveRule;
import com.sasimilkservice.incentive.domain.ProducerIncentive;
import com.sasimilkservice.incentive.repository.IncentiveRuleRepository;
import com.sasimilkservice.incentive.repository.ProducerIncentiveRepository;
import com.sasimilkservice.incentive.spi.IncentiveCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resolves the effective incentive rate for a collection: the non-individual
 * ("common") rate is taken region-first, combined with any individual rate per
 * {@link Stacking}, then clamped to {@code maxPerLitre} (client rule: total
 * incentive per litre never exceeds ₹2).
 */
@Service
@Transactional(readOnly = true)
public class IncentiveCalculatorImpl implements IncentiveCalculator {

    private final IncentiveRuleRepository commonRules;
    private final ProducerIncentiveRepository individualRules;

    public IncentiveCalculatorImpl(IncentiveRuleRepository commonRules,
                                   ProducerIncentiveRepository individualRules) {
        this.commonRules = commonRules;
        this.individualRules = individualRules;
    }

    @Override
    public IncentiveResult calculate(IncentiveQuery q) {
        Resolved common = resolveCommon(q);

        ProducerIncentive individual = first(individualRules.findActiveCovering(q.producerId(), q.collectionDate()));
        BigDecimal individualRate = individual == null ? BigDecimal.ZERO : individual.getIncentiveRatePerLitre();
        Long individualRuleId = individual == null ? null : individual.getId();

        BigDecimal stacked = switch (q.stacking()) {
            case ADD -> common.rate().add(individualRate);
            case REPLACE -> individualRate.signum() > 0 ? individualRate : common.rate();
        };

        BigDecimal cap = MoneyMath.nz(q.maxPerLitre());
        boolean capped = cap.signum() > 0 && stacked.compareTo(cap) > 0;
        BigDecimal effective = capped ? cap : stacked;

        return new IncentiveResult(
                MoneyMath.rate(common.rate()),
                MoneyMath.rate(individualRate),
                MoneyMath.rate(effective),
                MoneyMath.amount(q.litres(), effective),
                capped,
                common.ruleId(),
                individualRuleId);
    }

    private Resolved resolveCommon(IncentiveQuery q) {
        if (q.regionId() != null) {
            IncentiveRule region = first(commonRules.findActiveCovering(
                    IncentiveRule.Scope.REGION, q.regionId(), q.collectionDate()));
            if (region != null) {
                return new Resolved(region.getIncentiveRatePerLitre(), region.getId());
            }
        }
        IncentiveRule common = first(commonRules.findActiveCovering(
                IncentiveRule.Scope.COMMON, null, q.collectionDate()));
        return common == null
                ? new Resolved(BigDecimal.ZERO, null)
                : new Resolved(common.getIncentiveRatePerLitre(), common.getId());
    }

    private static <T> T first(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private record Resolved(BigDecimal rate, Long ruleId) {
    }
}
