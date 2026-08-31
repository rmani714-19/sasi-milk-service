package com.sasimilkservice.producer.repository;

import com.sasimilkservice.producer.domain.ProducerRegionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProducerRegionHistoryRepository extends JpaRepository<ProducerRegionHistory, Long> {

    List<ProducerRegionHistory> findByProducerIdOrderByChangedAtDesc(Long producerId);
}
