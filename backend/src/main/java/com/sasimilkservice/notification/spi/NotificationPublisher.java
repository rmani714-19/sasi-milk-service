package com.sasimilkservice.notification.spi;

import java.util.Map;

/**
 * Published SPI of the {@code notification} module. Callers fire-and-forget: the
 * request is de-duplicated, queued, and delivered asynchronously — API requests
 * never block on SMS/WhatsApp/email (spec notifications.architecture).
 */
public interface NotificationPublisher {

    void publish(NotificationRequest request);

    /**
     * @param eventType  one of the spec {@code notifications.events} codes
     * @param templateCode notification_templates.code to render
     * @param producerId producer this concerns (nullable)
     * @param recipientOverride explicit phone/email; when null the producer's contact is used
     * @param entityType / entityId the business entity that triggered this
     * @param data       template variables
     */
    record NotificationRequest(
            String eventType,
            String templateCode,
            Long producerId,
            String recipientOverride,
            String entityType,
            Long entityId,
            Map<String, Object> data) {
    }
}
