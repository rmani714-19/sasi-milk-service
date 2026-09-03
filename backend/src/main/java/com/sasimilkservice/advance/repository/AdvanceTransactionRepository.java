package com.sasimilkservice.advance.repository;

import com.sasimilkservice.advance.domain.AdvanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvanceTransactionRepository extends JpaRepository<AdvanceTransaction, Long> {

    List<AdvanceTransaction> findByProducerIdOrderByIdDesc(Long producerId);

    List<AdvanceTransaction> findAllByOrderByIdDesc();

    List<AdvanceTransaction> findByAdvanceAccountId(Long advanceAccountId);

    List<AdvanceTransaction> findBySettlementItemIdAndTxnType(Long settlementItemId,
                                                             AdvanceTransaction.Type txnType);
}
