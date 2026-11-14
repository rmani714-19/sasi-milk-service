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

    /**
     * True when a live batch of the same frequency overlaps the period but with the other scope
     * (a region batch while creating an all-regions one, or the reverse) — the two would pay the same producers.
     */
    @Query("""
            select count(s) > 0 from Settlement s
            where s.paymentFrequency = :frequency and s.status <> com.sasimilkservice.settlement.domain.Settlement.Status.CANCELLED
              and s.periodStart <= :to and s.periodEnd >= :from
              and ((:regional = true and s.regionId is null) or (:regional = false and s.regionId is not null))
            """)
    boolean existsOverlapOfOtherScope(@org.springframework.data.repository.query.Param("frequency") Settlement.PaymentFrequency frequency,
                                      @org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
                                      @org.springframework.data.repository.query.Param("to") java.time.LocalDate to,
                                      @org.springframework.data.repository.query.Param("regional") boolean regional);

    @Query(value = "select nextval('settlement_number_seq')", nativeQuery = true)
    long nextSettlementNumberValue();
}
