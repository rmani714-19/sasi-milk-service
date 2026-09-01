package com.sasimilkservice.notification.service;

import com.sasimilkservice.notification.domain.Notification;
import com.sasimilkservice.notification.domain.NotificationTemplate;
import com.sasimilkservice.notification.domain.NotificationTemplate.Channel;
import com.sasimilkservice.notification.repository.NotificationChannelConfigRepository;
import com.sasimilkservice.notification.repository.NotificationRepository;
import com.sasimilkservice.notification.repository.NotificationTemplateRepository;
import com.sasimilkservice.notification.spi.NotificationPublisher;
import com.sasimilkservice.producer.spi.ProducerDirectory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Queues notifications (de-duplicated by {@code dedupe_key}) and drives delivery
 * off the request thread via a periodic {@link #sweep()}; delivery of each row
 * happens in its own transaction ({@link NotificationDispatcher}). In production
 * the queue is Amazon SQS.
 */
@Service
public class NotificationService implements NotificationPublisher {

    private static final String LOCALE = "en";

    private final NotificationTemplateRepository templates;
    private final NotificationChannelConfigRepository channels;
    private final NotificationRepository notifications;
    private final TemplateRenderer renderer;
    private final ProducerDirectory producers;
    private final NotificationDispatcher dispatcher;

    public NotificationService(NotificationTemplateRepository templates,
                               NotificationChannelConfigRepository channels,
                               NotificationRepository notifications,
                               TemplateRenderer renderer,
                               ProducerDirectory producers,
                               NotificationDispatcher dispatcher) {
        this.templates = templates;
        this.channels = channels;
        this.notifications = notifications;
        this.renderer = renderer;
        this.producers = producers;
        this.dispatcher = dispatcher;
    }

    @Override
    @Transactional
    public void publish(NotificationRequest request) {
        ProducerDirectory.ProducerContact contact = request.producerId() == null
                ? null
                : producers.contact(request.producerId()).orElse(null);

        for (Channel channel : Channel.values()) {
            boolean enabled = channels.findByChannel(channel)
                    .map(com.sasimilkservice.notification.domain.NotificationChannelConfig::isEnabled)
                    .orElse(false);
            if (!enabled) {
                continue;
            }
            NotificationTemplate template = templates
                    .findByCodeAndChannelAndLocale(request.templateCode(), channel, LOCALE)
                    .orElse(null);
            if (template == null || template.getStatus() != NotificationTemplate.Status.ACTIVE) {
                continue;
            }
            String recipient = resolveRecipient(channel, request.recipientOverride(), contact);
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            String dedupeKey = "%s:%s:%s:%s:%s".formatted(request.eventType(), request.entityType(),
                    request.entityId(), channel, recipient);
            if (notifications.existsByDedupeKey(dedupeKey)) {
                continue;
            }

            Notification n = new Notification();
            n.setEventType(request.eventType());
            n.setChannel(channel);
            n.setRecipient(recipient);
            n.setProducerId(request.producerId());
            n.setTemplateCode(request.templateCode());
            n.setEntityType(request.entityType());
            n.setEntityId(request.entityId());
            n.setDedupeKey(dedupeKey);
            n.setPayload(request.data());
            n.setRenderedBody(renderer.render(template.getBody(), request.data()));
            notifications.save(n);
        }
    }

    /** Delivers QUEUED notifications; also retries retryable FAILED ones. */
    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT10S")
    public void sweep() {
        drain(100);
    }

    public int drain(int max) {
        List<Notification> batch = notifications.findByStatusOrderByIdAsc(
                Notification.Status.QUEUED, Limit.of(max));
        int sent = 0;
        for (Notification n : batch) {
            if (dispatcher.deliver(n.getId())) {
                sent++;
            }
        }
        return sent;
    }

    private static String resolveRecipient(Channel channel, String override,
                                           ProducerDirectory.ProducerContact contact) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        if (contact == null) {
            return null;
        }
        return channel == Channel.EMAIL ? contact.email() : contact.mobileNumber();
    }
}
