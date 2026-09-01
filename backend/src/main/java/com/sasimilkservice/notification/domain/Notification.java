package com.sasimilkservice.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** A single queued/sent notification. {@code dedupe_key} UNIQUE prevents duplicate sends. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

    public enum Status { QUEUED, SENDING, SENT, DELIVERED, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationTemplate.Channel channel;

    @Column(nullable = false)
    private String recipient;

    @Column(name = "producer_id")
    private Long producerId;

    @Column(name = "template_code")
    private String templateCode;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "rendered_body")
    private String renderedBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private String provider;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "provider_status")
    private String providerStatus;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
