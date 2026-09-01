package com.sasimilkservice.notification.api;

import com.sasimilkservice.notification.domain.Notification;
import com.sasimilkservice.notification.domain.NotificationChannelConfig;
import com.sasimilkservice.notification.domain.NotificationTemplate;
import com.sasimilkservice.notification.domain.NotificationTemplate.Channel;
import com.sasimilkservice.notification.service.NotificationAdminService;
import com.sasimilkservice.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationAdminService admin;
    private final NotificationService service;

    public NotificationController(NotificationAdminService admin, NotificationService service) {
        this.admin = admin;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ALL','VIEW_DASHBOARD')")
    public List<NotificationResponse> recent(@RequestParam(required = false) Long producerId) {
        return admin.recent(producerId).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('ALL')")
    public List<TemplateResponse> templates() {
        return admin.listTemplates().stream().map(TemplateResponse::from).toList();
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('ALL')")
    public TemplateResponse updateTemplate(@PathVariable long id, @Valid @RequestBody UpdateTemplateRequest r) {
        return TemplateResponse.from(admin.updateTemplate(id, r.subject(), r.body(),
                r.providerTemplateId(), r.status()));
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAuthority('ALL')")
    public List<ChannelResponse> channels() {
        return admin.listChannels().stream().map(ChannelResponse::from).toList();
    }

    @PutMapping("/channels/{channel}")
    @PreAuthorize("hasAuthority('ALL')")
    public ChannelResponse setChannel(@PathVariable Channel channel,
                                      @Valid @RequestBody UpdateChannelRequest r) {
        return ChannelResponse.from(admin.setChannelEnabled(channel, r.enabled(), r.provider(), r.senderId()));
    }

    /** Manual drain of the notification queue (normally the scheduled sweep handles this). */
    @PostMapping("/process")
    @PreAuthorize("hasAuthority('ALL')")
    public ProcessResult process() {
        return new ProcessResult(service.drain(200));
    }

    public record UpdateTemplateRequest(String subject, @NotBlank String body,
                                        String providerTemplateId, NotificationTemplate.Status status) {
    }

    public record UpdateChannelRequest(boolean enabled, String provider, String senderId) {
    }

    public record ProcessResult(int delivered) {
    }

    public record TemplateResponse(Long id, String code, String channel, String locale,
                                   String subject, String body, String providerTemplateId, String status) {
        static TemplateResponse from(NotificationTemplate t) {
            return new TemplateResponse(t.getId(), t.getCode(), t.getChannel().name(), t.getLocale(),
                    t.getSubject(), t.getBody(), t.getProviderTemplateId(), t.getStatus().name());
        }
    }

    public record ChannelResponse(String channel, boolean enabled, String provider, String senderId) {
        static ChannelResponse from(NotificationChannelConfig c) {
            return new ChannelResponse(c.getChannel().name(), c.isEnabled(), c.getProvider(), c.getSenderId());
        }
    }

    public record NotificationResponse(Long id, String eventType, String channel, String recipient,
                                       Long producerId, String status, int attemptCount,
                                       String renderedBody, Instant sentAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getEventType(), n.getChannel().name(),
                    n.getRecipient(), n.getProducerId(), n.getStatus().name(), n.getAttemptCount(),
                    n.getRenderedBody(), n.getSentAt());
        }
    }
}
