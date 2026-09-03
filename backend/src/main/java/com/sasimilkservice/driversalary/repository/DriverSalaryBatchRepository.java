package com.sasimilkservice.driversalary.repository;

import com.sasimilkservice.driversalary.domain.DriverSalaryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DriverSalaryBatchRepository extends JpaRepository<DriverSalaryBatch, Long> {

    List<DriverSalaryBatch> findAllByOrderByPeriodMonthDescIdDesc();

    Optional<DriverSalaryBatch> findByPeriodMonthAndStatusNot(LocalDate periodMonth, DriverSalaryBatch.Status status);

    @Query(value = "select nextval('salary_batch_number_seq')", nativeQuery = true)
    long nextBatchNumberValue();
}
