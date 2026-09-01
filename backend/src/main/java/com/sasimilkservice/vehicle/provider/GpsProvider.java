package com.sasimilkservice.vehicle.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;

/**
 * Abstraction over a third-party GPS provider. The default {@code noop} bean does
 * nothing — real providers push fixes to {@code POST /vehicles/{id}/locations} or
 * are polled here.
 */
public interface GpsProvider {

    String name();

    /** Pull the latest fixes since {@code since} for known devices. */
    List<Fix> poll(Instant since);

    record Fix(String externalDeviceId, Instant recordedAt, double latitude, double longitude,
               Double speedKmph, Double headingDeg, Boolean ignition) {
    }

    @Configuration
    class NoopGpsProviderConfig {
        @Bean
        @ConditionalOnMissingBean(GpsProvider.class)
        GpsProvider noopGpsProvider() {
            return new GpsProvider() {
                @Override
                public String name() {
                    return "NOOP";
                }

                @Override
                public List<Fix> poll(Instant since) {
                    return List.of();
                }
            };
        }
    }
}
