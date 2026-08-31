package com.sasimilkservice.incentive.repository;

import com.sasimilkservice.incentive.domain.ProducerIncentive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProducerIncentiveRepository extends JpaRepository<ProducerIncentive, Long> {

    @Query("""
            select p from ProducerIncentive p
            where p.status = :status
              and p.producerId = :producerId
              and p.effectiveDate <= :date
              and (p.endDate is null or p.endDate >= :date)
            order by p.effectiveDate desc
            """)
    List<ProducerIncentive> findCovering(@Param("status") ProducerIncentive.Status status,
                                         @Param("producerId") Long producerId,
                                         @Param("date") LocalDate date);

    default List<ProducerIncentive> findActiveCovering(Long producerId, LocalDate date) {
        return findCovering(ProducerIncentive.Status.ACTIVE, producerId, date);
    }

    List<ProducerIncentive> findByProducerIdOrderByEffectiveDateDesc(Long producerId);
}
