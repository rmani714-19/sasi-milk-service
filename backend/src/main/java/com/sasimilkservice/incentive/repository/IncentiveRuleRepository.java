package com.sasimilkservice.incentive.repository;

import com.sasimilkservice.incentive.domain.IncentiveRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface IncentiveRuleRepository extends JpaRepository<IncentiveRule, Long> {

    @Query("""
            select r from IncentiveRule r
            where r.status = :status
              and r.scope = :scope
              and ((:regionId is null and r.regionId is null) or r.regionId = :regionId)
              and r.effectiveDate <= :date
              and (r.endDate is null or r.endDate >= :date)
            order by r.effectiveDate desc
            """)
    List<IncentiveRule> findCovering(@Param("status") IncentiveRule.Status status,
                                     @Param("scope") IncentiveRule.Scope scope,
                                     @Param("regionId") Long regionId,
                                     @Param("date") LocalDate date);

    default List<IncentiveRule> findActiveCovering(IncentiveRule.Scope scope, Long regionId, LocalDate date) {
        return findCovering(IncentiveRule.Status.ACTIVE, scope, regionId, date);
    }

    List<IncentiveRule> findByStatusOrderByEffectiveDateDesc(IncentiveRule.Status status);
}
