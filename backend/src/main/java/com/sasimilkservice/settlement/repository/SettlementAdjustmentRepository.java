package com.sasimilkservice.settlement.repository;

import com.sasimilkservice.settlement.domain.SettlementAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, Long> {

    List<SettlementAdjustment> findBySettlementIdAndStatus(Long settlementId,
                                                          SettlementAdjustment.Status status);

    List<SettlementAdjustment> findBySettlementId(Long settlementId);
}
