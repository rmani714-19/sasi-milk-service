package com.sasimilkservice.notification.repository;

import com.sasimilkservice.notification.domain.NotificationChannelConfig;
import com.sasimilkservice.notification.domain.NotificationTemplate.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationChannelConfigRepository extends JpaRepository<NotificationChannelConfig, Long> {

    Optional<NotificationChannelConfig> findByChannel(Channel channel);
}
