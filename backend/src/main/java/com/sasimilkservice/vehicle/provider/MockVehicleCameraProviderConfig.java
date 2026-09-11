package com.sasimilkservice.vehicle.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link VehicleCameraProvider} — reports every camera offline and
 * returns no stream/playback/recordings. This lets the whole Live Camera
 * screen (camera list, offline state, "no recordings") be built and tested
 * before any real vendor is connected, without inventing a fake video URL.
 * A real vendor adapter supplies its own bean and this one backs off (see
 * {@code sasi.vehicle.provider.camera.enabled}).
 */
@Configuration
public class MockVehicleCameraProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sasi.vehicle.provider.camera", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(VehicleCameraProvider.class)
    VehicleCameraProvider mockVehicleCameraProvider() {
        return new VehicleCameraProvider() {

            @Override
            public String name() {
                return "MOCK";
            }

            @Override
            public CameraStatus getCameraStatus(String providerCameraId) {
                return new CameraStatus(false, null);
            }

            @Override
            public Optional<LiveStreamInfo> getLiveStream(String providerCameraId) {
                return Optional.empty();
            }

            @Override
            public Optional<PlaybackInfo> getPlayback(String providerCameraId, Instant from, Instant to) {
                return Optional.empty();
            }

            @Override
            public List<RecordingSummary> getRecordingList(String providerCameraId, Instant from, Instant to) {
                return List.of();
            }
        };
    }
}
