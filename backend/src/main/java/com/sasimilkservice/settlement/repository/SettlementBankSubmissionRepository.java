package com.sasimilkservice.settlement.repository;

import com.sasimilkservice.settlement.domain.SettlementBankSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementBankSubmissionRepository extends JpaRepository<SettlementBankSubmission, Long> {

    Optional<SettlementBankSubmission> findBySettlementId(Long settlementId);
}
