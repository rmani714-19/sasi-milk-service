package com.sasimilkservice.driversalary.repository;

import com.sasimilkservice.driversalary.domain.DriverSalaryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DriverSalaryItemRepository extends JpaRepository<DriverSalaryItem, Long> {

    List<DriverSalaryItem> findByBatchIdOrderByIdAsc(Long batchId);

    List<DriverSalaryItem> findAllByOrderByIdDesc();
}
