package com.sasimilkservice.vehicle.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstraction over a GPS tracking vendor. The business application never
 * calls a vendor SDK or hardcodes a vendor protocol directly — it talks to
 * this interface, and a real adapter is added later once vendor hardware
 * and API documentation are confirmed (see docs on the mock implementation).
 *
 * <p>Our own database (vehicle_latest_locations / vehicle_location_history)
 * is the source of truth the REST API actually reads from — it is kept
 * current either by a provider pushing to the webhook endpoint, or by a
 * scheduled poller calling {@link #getLocationHistory} on a provider that
 * only offers a pull API. Live UI reads never block on a call to this
 * interface.
 */
public interface VehicleTrackingProvider {

    String name();

    Optional<LocationFix> getLatestLocation(String externalDeviceId);

    VehicleStatusInfo getVehicleStatus(String externalDeviceId);

    List<LocationFix> getLocationHistory(String externalDeviceId, Instant from, Instant to);

    List<TripSummary> getTripHistory(String externalDeviceId, Instant from, Instant to);

    DeviceHealth getDeviceStatus(String externalDeviceId);

    // ---- optional — most devices/providers do not support these; never assume they do ----

    default CommandResult sendCommand(String externalDeviceId, String command, Map<String, Object> params) {
        return CommandResult.unsupported();
    }

    default CommandResult requestLocation(String externalDeviceId) {
        return CommandResult.unsupported();
    }

    /** True if the provider offers a push/stream channel beyond periodic fixes. */
    default boolean startLiveTracking(String externalDeviceId) {
        return false;
    }

    record LocationFix(
            String externalDeviceId,
            Instant providerTimestamp,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKmph,
            BigDecimal directionDegrees,
            BigDecimal altitude,
            String ignitionStatus,
            String motionStatus,
            BigDecimal gpsAccuracy) {
    }

    record VehicleStatusInfo(boolean online, String motionStatus, String ignitionStatus, LocationFix lastFix) {
    }

    record TripSummary(
            Instant startedAt,
            Instant endedAt,
            BigDecimal startLatitude,
            BigDecimal startLongitude,
            BigDecimal endLatitude,
            BigDecimal endLongitude,
            BigDecimal distanceKm,
            Long durationSeconds,
            BigDecimal maxSpeedKmph,
            BigDecimal averageSpeedKmph) {
    }

    record DeviceHealth(boolean online, Instant lastSeenAt, String firmwareVersion) {
    }

    record CommandResult(boolean accepted, boolean supported, String message) {
        public static CommandResult unsupported() {
            return new CommandResult(false, false, "Not supported by this provider or device");
        }
    }
}
