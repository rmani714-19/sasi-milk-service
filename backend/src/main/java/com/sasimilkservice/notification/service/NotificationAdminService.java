package com.sasimilkservice.notification.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.notification.domain.Notification;
import com.sasimilkservice.notification.domain.NotificationChannelConfig;
import com.sasimilkservice.notification.domain.NotificationTemplate;
import com.sasimilkservice.notification.domain.NotificationTemplate.Channel;
import com.sasimilkservice.notification.repository.NotificationChannelConfigRepository;
import com.sasimilkservice.notification.repository.NotificationRepository;
import com.sasimilkservice.notification.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationAdminService {

    private final NotificationTemplateRepository templates;
    private final NotificationChannelConfigRepository channels;
    private final NotificationRepository notifications;
    private final AuditRecorder audit;

    public NotificationAdminService(NotificationTemplateRepository templates,
                                    NotificationChannelConfigRepository channels,
                                    NotificationRepository notifications,
                                    AuditRecorder audit) {
        this.templates = templates;
        this.channels = channels;
        this.notifications = notifications;
        this.audit = audit;
    }

    public List<NotificationTemplate> listTemplates() {
        return templates.findAllByOrderByCodeAscChannelAsc();
    }

    public List<NotificationChannelConfig> listChannels() {
        return channels.findAll();
    }

    public List<Notification> recent(Long producerId) {
        return producerId == null
                ? notifications.findTop200ByOrderByIdDesc()
                : notifications.findByProducerIdOrderByIdDesc(producerId);
    }

    @Transactional
    public NotificationTemplate updateTemplate(long id, String subject, String body,
                                               String providerTemplateId, NotificationTemplate.Status status) {
        NotificationTemplate t = templates.findById(id)
                .orElseThrow(() -> NotFoundException.of("NotificationTemplate", id));
        t.setSubject(subject);
        t.setBody(body);
        t.setProviderTemplateId(providerTemplateId);
        if (status != null) {
            t.setStatus(status);
        }
        audit.record("NOTIFICATION_SENT", "NotificationTemplate", id,
                "Template " + t.getCode() + "/" + t.getChannel() + " updated");
        return t;
    }

    @Transactional
    public NotificationChannelConfig setChannelEnabled(Channel channel, boolean enabled,
                                                       String provider, String senderId) {
        NotificationChannelConfig c = channels.findByChannel(channel)
                .orElseGet(() -> {
                    NotificationChannelConfig fresh = new NotificationChannelConfig();
                    fresh.setChannel(channel);
                    return fresh;
                });
        c.setEnabled(enabled);
        if (provider != null) {
            c.setProvider(provider);
        }
        if (senderId != null) {
            c.setSenderId(senderId);
        }
        c = channels.save(c);
        audit.record("NOTIFICATION_SENT", "NotificationChannelConfig", c.getId(),
                channel + " channel " + (enabled ? "enabled" : "disabled"));
        return c;
    }
}
