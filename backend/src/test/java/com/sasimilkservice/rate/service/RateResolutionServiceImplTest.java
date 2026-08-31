package com.sasimilkservice.rate.service;

import com.sasimilkservice.rate.domain.RateChart;
import com.sasimilkservice.rate.repository.RateChartRepository;
import com.sasimilkservice.rate.spi.RateNotConfiguredException;
import com.sasimilkservice.rate.spi.RateResolutionService.ResolveRateQuery;
import com.sasimilkservice.rate.spi.RateResolutionService.ResolvedRate;
import com.sasimilkservice.rate.spi.RateResolutionService.RateScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateResolutionServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 31);
    private static final long REGION = 2L;

    private RateChartRepository repo;
    private RateResolutionServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(RateChartRepository.class);
        service = new RateResolutionServiceImpl(repo);
        when(repo.findActiveCovering(any(), any(), any())).thenReturn(List.of());
    }

    private RateChart chart(RateChart.Scope scope, String ts) {
        RateChart c = new RateChart();
        c.setScope(scope);
        c.setTsValue(new BigDecimal(ts));
        return c;
    }

    private ResolveRateQuery query(Long regionId, boolean allowMissing) {
        return new ResolveRateQuery(regionId, DAY, new BigDecimal("4.1"), new BigDecimal("8.6"), allowMissing);
    }

    @Test
    void uses_region_chart_when_present() {
        when(repo.findActiveCovering(RateChart.Scope.REGION, REGION, DAY))
                .thenReturn(List.of(chart(RateChart.Scope.REGION, "270")));
        when(repo.findActiveCovering(RateChart.Scope.COMMON, null, DAY))
                .thenReturn(List.of(chart(RateChart.Scope.COMMON, "265")));

        ResolvedRate r = service.resolve(query(REGION, false));

        assertThat(r.scope()).isEqualTo(RateScope.REGION);
        assertThat(r.tsValue()).isEqualByComparingTo("270");
        // (4.1 + 8.6) * 270 / 100
        assertThat(r.ratePerLitre()).isEqualByComparingTo("34.2900");
    }

    @Test
    void falls_back_to_common_when_no_region_chart() {
        when(repo.findActiveCovering(RateChart.Scope.COMMON, null, DAY))
                .thenReturn(List.of(chart(RateChart.Scope.COMMON, "265")));

        ResolvedRate r = service.resolve(query(REGION, false));

        assertThat(r.scope()).isEqualTo(RateScope.COMMON);
        assertThat(r.ratePerLitre()).isEqualByComparingTo("33.6550");
    }

    @Test
    void throws_when_no_chart_and_missing_not_allowed() {
        assertThatThrownBy(() -> service.resolve(query(REGION, false)))
                .isInstanceOf(RateNotConfiguredException.class);
    }

    @Test
    void returns_zero_rate_when_missing_allowed() {
        ResolvedRate r = service.resolve(query(REGION, true));
        assertThat(r.missing()).isTrue();
        assertThat(r.ratePerLitre()).isEqualByComparingTo("0");
        assertThat(service.tryResolve(query(REGION, false))).isEmpty();
    }
}
