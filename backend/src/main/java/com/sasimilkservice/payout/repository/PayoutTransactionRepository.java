package com.sasimilkservice.payout.repository;

import com.sasimilkservice.payout.domain.PayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, Long> {

    List<PayoutTransaction> findByPayoutIdOrderByIdAsc(Long payoutId);
}
