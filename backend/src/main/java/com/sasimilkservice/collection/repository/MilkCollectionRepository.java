package com.sasimilkservice.collection.repository;

import com.sasimilkservice.collection.domain.MilkCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MilkCollectionRepository extends JpaRepository<MilkCollection, Long> {

    Optional<MilkCollection> findByClientTransactionId(UUID clientTransactionId);

    Optional<MilkCollection> findByPublicId(UUID publicId);

    @Query("""
            select c from MilkCollection c
            where (:producerId is null or c.producerId = :producerId)
              and (:date is null or c.collectionDate = :date)
              and (:shift is null or c.shift = :shift)
              and (:status is null or c.status = :status)
            order by c.collectionDate desc, c.id desc
            """)
    Page<MilkCollection> search(@Param("producerId") Long producerId,
                                @Param("date") LocalDate date,
                                @Param("shift") String shift,
                                @Param("status") MilkCollection.Status status,
                                Pageable pageable);

    @Query(value = "select nextval('collection_number_seq')", nativeQuery = true)
    long nextCollectionNumberValue();
}
