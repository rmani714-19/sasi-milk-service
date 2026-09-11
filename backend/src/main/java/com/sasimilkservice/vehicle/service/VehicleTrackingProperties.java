package com.sasimilkservice.vehicle.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Binds {@code sasi.vehicle.*} — the operational thresholds used to derive
 * online/offline state, overspeed alerts, and trip boundaries from raw
 * telemetry. Kept configurable per the spec rather than hardcoded.
 *
 * @param onlineWindow             a vehicle is "online" if its last fix is within this window of now
 * @param overspeedThresholdKmph   a fix at or above this speed raises an OVERSPEED alert
 * @param deviceOfflineCheckWindow a device with no fix for longer than this raises DEVICE_OFFLINE
 * @param trip                     trip-detection thresholds
 */
@ConfigurationProperties(prefix = "sasi.vehicle")
public record VehicleTrackingProperties(
        Duration onlineWindow,
        BigDecimal overspeedThresholdKmph,
        Duration deviceOfflineCheckWindow,
        Trip trip) {

    public VehicleTrackingProperties {
        if (onlineWindow == null) onlineWindow = Duration.ofMinutes(15);
        if (overspeedThresholdKmph == null) overspeedThresholdKmph = new BigDecimal("80");
        if (deviceOfflineCheckWindow == null) deviceOfflineCheckWindow = Duration.ofMinutes(30);
        if (trip == null) trip = new Trip(null, null);
    }

    /**
     * @param minDuration              a trip shorter than this is discarded as noise rather than saved
     * @param movingSpeedThresholdKmph a fix at or above this speed (or ignition ON) is treated as "moving"
     */
    public record Trip(Duration minDuration, BigDecimal movingSpeedThresholdKmph) {
        public Trip {
            if (minDuration == null) minDuration = Duration.ofMinutes(3);
            if (movingSpeedThresholdKmph == null) movingSpeedThresholdKmph = new BigDecimal("5");
        }
    }
}
