package com.sasimilkservice.settlement.repository;

import com.sasimilkservice.settlement.domain.SettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    List<SettlementItem> findBySettlementIdOrderByProducerIdAsc(Long settlementId);

    void deleteBySettlementId(Long settlementId);
}
