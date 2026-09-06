package com.sasimilkservice.collection.repository;

import com.sasimilkservice.collection.domain.MilkCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilkCollectionRepository extends JpaRepository<MilkCollection, Long> {

    Optional<MilkCollection> findByClientTransactionId(UUID clientTransactionId);

    Optional<MilkCollection> findByPublicId(UUID publicId);

    // casts type the parameters so PostgreSQL can evaluate the `:x is null` checks
    @Query("""
            select c from MilkCollection c
            where (cast(:producerId as long) is null or c.producerId = :producerId)
              and (cast(:date as date) is null or c.collectionDate = :date)
              and (cast(:shift as string) is null or c.shift = :shift)
              and (cast(:status as string) is null or c.status = :status)
            order by c.collectionDate desc, c.id desc
            """)
    Page<MilkCollection> search(@Param("producerId") Long producerId,
                                @Param("date") LocalDate date,
                                @Param("shift") String shift,
                                @Param("status") MilkCollection.Status status,
                                Pageable pageable);

    @Query(value = "select nextval('collection_number_seq')", nativeQuery = true)
    long nextCollectionNumberValue();

    List<MilkCollection> findByStatusAndSettlementItemIdIsNullAndCollectionDateBetween(
            MilkCollection.Status status, LocalDate start, LocalDate end);

    long countByStatusAndCollectionDateBetween(MilkCollection.Status status, LocalDate start, LocalDate end);

    List<MilkCollection> findBySettlementItemId(Long settlementItemId);
}
