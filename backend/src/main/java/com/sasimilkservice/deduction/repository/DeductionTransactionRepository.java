package com.sasimilkservice.deduction.repository;

import com.sasimilkservice.deduction.domain.DeductionTransaction;
import com.sasimilkservice.deduction.domain.DeductionTransaction.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DeductionTransactionRepository extends JpaRepository<DeductionTransaction, Long> {

    List<DeductionTransaction> findByProducerIdOrderByTxnDateDescIdDesc(Long producerId);

    List<DeductionTransaction> findAllByOrderByTxnDateDescIdDesc();

    @Query("""
            select d from DeductionTransaction d
            where d.status = :status
              and d.producerId = :producerId
              and d.txnDate <= :asOf
            order by d.txnDate asc, d.id asc
            """)
    List<DeductionTransaction> findByStatusAndProducer(@Param("status") Status status,
                                                       @Param("producerId") Long producerId,
                                                       @Param("asOf") LocalDate asOf);

    default List<DeductionTransaction> findPending(Long producerId, LocalDate asOf) {
        return findByStatusAndProducer(Status.PENDING, producerId, asOf);
    }

    List<DeductionTransaction> findBySettlementItemIdIn(List<Long> settlementItemIds);
}
