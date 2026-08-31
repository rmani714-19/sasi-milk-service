package com.sasimilkservice.rate.repository;

import com.sasimilkservice.rate.domain.RateChart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RateChartRepository extends JpaRepository<RateChart, Long> {

    @Query("""
            select r from RateChart r
            where r.status = :status
              and r.scope = :scope
              and ((:regionId is null and r.regionId is null) or r.regionId = :regionId)
              and r.effectiveFrom <= :date
              and (r.effectiveTo is null or r.effectiveTo >= :date)
            order by r.effectiveFrom desc
            """)
    List<RateChart> findCovering(@Param("status") RateChart.Status status,
                                 @Param("scope") RateChart.Scope scope,
                                 @Param("regionId") Long regionId,
                                 @Param("date") LocalDate date);

    default List<RateChart> findActiveCovering(RateChart.Scope scope, Long regionId, LocalDate date) {
        return findCovering(RateChart.Status.ACTIVE, scope, regionId, date);
    }

    List<RateChart> findByScopeOrderByEffectiveFromDesc(RateChart.Scope scope);

    List<RateChart> findAllByOrderByScopeAscEffectiveFromDesc();
}
