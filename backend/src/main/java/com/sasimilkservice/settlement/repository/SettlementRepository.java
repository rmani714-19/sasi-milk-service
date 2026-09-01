package com.sasimilkservice.settlement.repository;

import com.sasimilkservice.settlement.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByPublicId(UUID publicId);

    List<Settlement> findAllByOrderByPeriodStartDesc();

    @Query(value = "select nextval('settlement_number_seq')", nativeQuery = true)
    long nextSettlementNumberValue();
}
