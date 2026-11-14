package com.sasimilkservice.payout.repository;

import com.sasimilkservice.payout.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Optional<Payout> findByPublicId(UUID publicId);

    Optional<Payout> findBySettlementItemId(Long settlementItemId);


    List<Payout> findBySettlementIdOrderByProducerIdAsc(Long settlementId);

    List<Payout> findAllByOrderByIdDesc();
}
