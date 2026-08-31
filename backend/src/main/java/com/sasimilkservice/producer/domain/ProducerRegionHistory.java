package com.sasimilkservice.producer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Append-only record of a producer's region reassignments. */
@Entity
@Table(name = "producer_region_history")
@Getter
@Setter
public class ProducerRegionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "old_region_id")
    private Long oldRegionId;

    @Column(name = "new_region_id")
    private Long newRegionId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "changed_by")
    private Long changedBy;

    private String reason;
}
