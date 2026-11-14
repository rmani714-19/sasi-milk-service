package com.sasimilkservice.settlement.repository;

import com.sasimilkservice.settlement.domain.SettlementStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementStatusHistoryRepository extends JpaRepository<SettlementStatusHistory, Long> {

    List<SettlementStatusHistory> findBySettlementIdOrderByIdDesc(Long settlementId);
}
