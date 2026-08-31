package com.sasimilkservice.rate.service;

import com.sasimilkservice.rate.domain.RateChart;
import com.sasimilkservice.rate.repository.RateChartRepository;
import com.sasimilkservice.rate.spi.RateFormula;
import com.sasimilkservice.rate.spi.RateNotConfiguredException;
import com.sasimilkservice.rate.spi.RateResolutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RateResolutionServiceImpl implements RateResolutionService {

    private final RateChartRepository rateCharts;

    public RateResolutionServiceImpl(RateChartRepository rateCharts) {
        this.rateCharts = rateCharts;
    }

    @Override
    public ResolvedRate resolve(ResolveRateQuery query) {
        if (query.regionId() != null) {
            Optional<RateChart> regional = first(
                    rateCharts.findActiveCovering(RateChart.Scope.REGION, query.regionId(), query.collectionDate()));
            if (regional.isPresent()) {
                return build(regional.get(), RateScope.REGION, query);
            }
        }
        Optional<RateChart> common = first(
                rateCharts.findActiveCovering(RateChart.Scope.COMMON, null, query.collectionDate()));
        if (common.isPresent()) {
            return build(common.get(), RateScope.COMMON, query);
        }
        if (query.allowMissingRate()) {
            return new ResolvedRate(BigDecimal.ZERO, BigDecimal.ZERO, RateScope.COMMON, null, true);
        }
        throw new RateNotConfiguredException(query.regionId(), query.collectionDate(),
                query.fatPercentage(), query.snfPercentage());
    }

    @Override
    public Optional<ResolvedRate> tryResolve(ResolveRateQuery query) {
        try {
            return Optional.of(resolve(query));
        } catch (RateNotConfiguredException ex) {
            return Optional.empty();
        }
    }

    private ResolvedRate build(RateChart chart, RateScope scope, ResolveRateQuery query) {
        BigDecimal rate = RateFormula.ratePerLitre(
                query.fatPercentage(), query.snfPercentage(), chart.getTsValue());
        return new ResolvedRate(rate, chart.getTsValue(), scope, chart.getId(), false);
    }

    private static Optional<RateChart> first(List<RateChart> charts) {
        return charts.isEmpty() ? Optional.empty() : Optional.of(charts.get(0));
    }
}
