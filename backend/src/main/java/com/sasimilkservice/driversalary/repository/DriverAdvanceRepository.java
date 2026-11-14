package com.sasimilkservice.driversalary.repository;

import com.sasimilkservice.driversalary.domain.DriverAdvance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DriverAdvanceRepository extends JpaRepository<DriverAdvance, Long> {

    List<DriverAdvance> findByDriverIdOrderByAdvanceDateDescIdDesc(Long driverId);

    List<DriverAdvance> findByDriverIdAndStatusAndAdvanceDateLessThanEqualOrderByAdvanceDateAscIdAsc(
            Long driverId, DriverAdvance.Status status, LocalDate onOrBefore);

    List<DriverAdvance> findByDeductedInItemId(Long itemId);
}
