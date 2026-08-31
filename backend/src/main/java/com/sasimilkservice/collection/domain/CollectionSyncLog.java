package com.sasimilkservice.collection.domain;

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
import java.util.UUID;

/** Raw record of each collection submission (online or offline replay), for audit + replay. */
@Entity
@Table(name = "collection_sync_log")
@Getter
@Setter
public class CollectionSyncLog {

    public enum Outcome { APPLIED, DUPLICATE, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_transaction_id", nullable = false)
    private UUID clientTransactionId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(name = "collection_id")
    private Long collectionId;

    @Column(name = "error_detail")
    private String errorDetail;
}
