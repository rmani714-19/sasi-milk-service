package com.sasimilkservice.vehicle.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link VehicleTrackingProvider} for development and for any device
 * that has no pull API of its own — telemetry arrives only via
 * {@code POST /api/v1/vehicles/providers/{provider}/webhook} or the manual
 * ingest endpoint, both of which write straight into
 * vehicle_latest_locations / vehicle_location_history. This bean honestly
 * reports "nothing to pull" rather than inventing fixture data; a real
 * vendor adapter supplies its own {@link VehicleTrackingProvider} bean and
 * this one backs off (see {@code sasi.vehicle.provider.tracking.enabled}).
 */
@Configuration
public class MockVehicleTrackingProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sasi.vehicle.provider.tracking", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(VehicleTrackingProvider.class)
    VehicleTrackingProvider mockVehicleTrackingProvider() {
        return new VehicleTrackingProvider() {

            @Override
            public String name() {
                return "MOCK";
            }

            @Override
            public Optional<LocationFix> getLatestLocation(String externalDeviceId) {
                return Optional.empty();
            }

            @Override
            public VehicleStatusInfo getVehicleStatus(String externalDeviceId) {
                return new VehicleStatusInfo(false, "UNKNOWN", "UNKNOWN", null);
            }

            @Override
            public List<LocationFix> getLocationHistory(String externalDeviceId, Instant from, Instant to) {
                return List.of();
            }

            @Override
            public List<TripSummary> getTripHistory(String externalDeviceId, Instant from, Instant to) {
                return List.of();
            }

            @Override
            public DeviceHealth getDeviceStatus(String externalDeviceId) {
                return new DeviceHealth(false, null, null);
            }
        };
    }
}
