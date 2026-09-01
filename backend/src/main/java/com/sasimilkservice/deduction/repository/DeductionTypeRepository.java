package com.sasimilkservice.deduction.repository;

import com.sasimilkservice.deduction.domain.DeductionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeductionTypeRepository extends JpaRepository<DeductionType, Long> {

    Optional<DeductionType> findByCode(String code);
}
