package com.sasimilkservice.driversalary.repository;

import com.sasimilkservice.driversalary.domain.DriverSalaryBankSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverSalaryBankSubmissionRepository extends JpaRepository<DriverSalaryBankSubmission, Long> {

    Optional<DriverSalaryBankSubmission> findByBatchId(Long batchId);
}
