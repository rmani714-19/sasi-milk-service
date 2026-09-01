package com.sasimilkservice.notification.repository;

import com.sasimilkservice.notification.domain.NotificationTemplate;
import com.sasimilkservice.notification.domain.NotificationTemplate.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByCodeAndChannelAndLocale(String code, Channel channel, String locale);

    List<NotificationTemplate> findAllByOrderByCodeAscChannelAsc();
}
