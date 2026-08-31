package com.sasimilkservice.collection.repository;

import com.sasimilkservice.collection.domain.CollectionSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionSyncLogRepository extends JpaRepository<CollectionSyncLog, Long> {
}
