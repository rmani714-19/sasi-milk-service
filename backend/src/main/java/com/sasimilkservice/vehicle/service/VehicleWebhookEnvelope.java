package com.sasimilkservice.vehicle.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Provider-agnostic shape the webhook endpoint accepts today. No real vendor
 * has been selected yet, so this is deliberately a neutral envelope rather
 * than any particular vendor's payload — once a device/vendor is chosen and
 * their webhook format is documented, a small mapper translates their raw
 * payload into this envelope (or this class grows a vendor-specific sibling)
 * without touching the ingestion/alerting logic below it.
 */
public record VehicleWebhookEnvelope(
        String eventId,
        String eventType,
        String externalDeviceId,
        Instant timestamp,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speedKmph,
        BigDecimal directionDegrees,
        BigDecimal altitude,
        String ignitionStatus,
        String motionStatus,
        BigDecimal gpsAccuracy,
        Map<String, Object> raw) {
}
