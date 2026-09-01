package com.sasimilkservice.notification.service;

import com.sasimilkservice.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Delivers a rendered notification over its channel. The default implementation
 * logs the message and reports success; real SMS (India DLT) / WhatsApp (BSP) /
 * email (SES) adapters replace this bean when providers are configured.
 */
public interface ChannelSender {

    SendResult send(Notification notification);

    record SendResult(boolean success, String providerMessageId, String providerStatus, String error) {
        public static SendResult ok(String id) {
            return new SendResult(true, id, "SENT", null);
        }

        public static SendResult failed(String error) {
            return new SendResult(false, null, "FAILED", error);
        }
    }

    @Component
    class LoggingChannelSender implements ChannelSender {

        private static final Logger log = LoggerFactory.getLogger(LoggingChannelSender.class);

        @Override
        public SendResult send(Notification n) {
            log.info("[notification:{}] to {} :: {}",
                    n.getChannel(), n.getRecipient(), n.getRenderedBody());
            return SendResult.ok("LOG-" + UUID.randomUUID());
        }
    }
}
