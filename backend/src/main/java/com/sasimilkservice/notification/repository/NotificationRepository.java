package com.sasimilkservice.notification.repository;

import com.sasimilkservice.notification.domain.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<Notification> findByStatusOrderByIdAsc(Notification.Status status, Limit limit);

    List<Notification> findByProducerIdOrderByIdDesc(Long producerId);

    List<Notification> findTop200ByOrderByIdDesc();
}
