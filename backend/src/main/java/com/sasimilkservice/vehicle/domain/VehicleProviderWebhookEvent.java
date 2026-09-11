package com.sasimilkservice.vehicle.domain;

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
import java.util.Map;

/**
 * One inbound provider webhook call, kept for idempotency and audit —
 * mirrors {@code payment_webhook_events} from the payout module. Dedupe key
 * is {@code (provider, event_id)}; a redelivery is recognised and ignored
 * rather than reprocessed.
 */
@Entity
@Table(name = "vehicle_provider_webhook_events")
@Getter
@Setter
public class VehicleProviderWebhookEvent {

    public enum Status { RECEIVED, PROCESSED, DUPLICATE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type")
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RECEIVED;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "received_at", nullable = false, updatable = false, insertable = false)
    private Instant receivedAt;
}
