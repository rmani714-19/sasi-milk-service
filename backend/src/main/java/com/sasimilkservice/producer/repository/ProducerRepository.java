package com.sasimilkservice.producer.repository;

import com.sasimilkservice.producer.domain.Producer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProducerRepository extends JpaRepository<Producer, Long> {

    Optional<Producer> findByProducerNumber(String producerNumber);

    Optional<Producer> findByQrCode(String qrCode);

    Optional<Producer> findByPublicId(UUID publicId);

    // :q is passed as "" (never null) so PostgreSQL can type it; casts type the other optional filters
    @Query("""
            select p from Producer p
            where (cast(:status as string) is null or p.status = :status)
              and (cast(:regionId as long) is null or p.regionId = :regionId)
              and (:q = ''
                   or lower(p.producerName) like lower(concat('%', :q, '%'))
                   or lower(p.producerNumber) like lower(concat('%', :q, '%')))
            """)
    Page<Producer> search(@Param("status") Producer.Status status,
                          @Param("regionId") Long regionId,
                          @Param("q") String q,
                          Pageable pageable);

    @Query(value = "select nextval('producer_number_seq')", nativeQuery = true)
    long nextProducerNumberValue();
}
