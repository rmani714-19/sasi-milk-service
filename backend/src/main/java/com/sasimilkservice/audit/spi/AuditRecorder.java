package com.sasimilkservice.audit.spi;

import java.util.Map;

/**
 * Published SPI of the {@code audit} module. Modules call this inside the same
 * transaction as the change they are recording.
 */
public interface AuditRecorder {

    void record(AuditEvent event);

    default void record(String action, String entityType, Object entityId, String summary) {
        record(new AuditEvent(action, entityType, entityId == null ? null : entityId.toString(),
                null, null, summary));
    }

    default void recordChange(String action, String entityType, Object entityId,
                              Map<String, Object> oldValue, Map<String, Object> newValue, String summary) {
        record(new AuditEvent(action, entityType, entityId == null ? null : entityId.toString(),
                oldValue, newValue, summary));
    }

    /**
     * @param action     one of the spec {@code audit.events} codes
     * @param entityType simple entity name
     * @param entityId   business or surrogate id, as text
     * @param oldValue   prior state (nullable)
     * @param newValue   new state (nullable)
     * @param summary    short human-readable description
     */
    record AuditEvent(
            String action,
            String entityType,
            String entityId,
            Map<String, Object> oldValue,
            Map<String, Object> newValue,
            String summary) {
    }
}
