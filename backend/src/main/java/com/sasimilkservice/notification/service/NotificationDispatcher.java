package com.sasimilkservice.notification.service;

import com.sasimilkservice.notification.domain.Notification;
import com.sasimilkservice.notification.repository.NotificationRepository;
import com.sasimilkservice.notification.service.ChannelSender.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Delivers one notification in its own transaction so a single failure does not
 * roll back a whole batch. Separate bean from {@link NotificationService} so the
 * {@code @Transactional} boundary is honoured (no self-invocation).
 */
@Component
public class NotificationDispatcher {

    private static final int MAX_ATTEMPTS = 5;

    private final NotificationRepository notifications;
    private final ChannelSender sender;

    public NotificationDispatcher(NotificationRepository notifications, ChannelSender sender) {
        this.notifications = notifications;
        this.sender = sender;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(long notificationId) {
        Notification n = notifications.findById(notificationId).orElse(null);
        if (n == null || (n.getStatus() != Notification.Status.QUEUED
                && n.getStatus() != Notification.Status.FAILED)) {
            return false;
        }
        n.setStatus(Notification.Status.SENDING);
        n.setAttemptCount(n.getAttemptCount() + 1);
        n.setUpdatedAt(Instant.now());

        SendResult result;
        try {
            result = sender.send(n);
        } catch (RuntimeException ex) {
            result = SendResult.failed(ex.getMessage());
        }

        if (result.success()) {
            n.setStatus(Notification.Status.SENT);
            n.setSentAt(Instant.now());
            n.setProviderMessageId(result.providerMessageId());
            n.setProviderStatus(result.providerStatus());
            n.setErrorDetail(null);
        } else {
            n.setStatus(n.getAttemptCount() >= MAX_ATTEMPTS
                    ? Notification.Status.FAILED : Notification.Status.QUEUED);
            n.setErrorDetail(result.error());
        }
        n.setUpdatedAt(Instant.now());
        return result.success();
    }
}
