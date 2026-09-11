package com.sasimilkservice.vehicle.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code sasi.vehicle.provider.*}. Real values come from the
 * environment / a secret manager (AWS Secrets Manager in production) —
 * never committed and never exposed to the frontend. Both providers default
 * to disabled, which keeps the MOCK beans active (see
 * {@code MockVehicleTrackingProviderConfig} / {@code MockVehicleCameraProviderConfig}).
 * A real vendor adapter is added once the device/vendor and its API,
 * authentication, streaming protocol and webhook format are confirmed —
 * this record only reserves the shape.
 */
@ConfigurationProperties(prefix = "sasi.vehicle.provider")
public record VehicleProviderProperties(Endpoint tracking, Endpoint camera) {

    public VehicleProviderProperties {
        if (tracking == null) tracking = new Endpoint(false, null, null, null, null);
        if (camera == null) camera = new Endpoint(false, null, null, null, null);
    }

    /**
     * @param enabled       register the real adapter bean instead of the mock
     * @param baseUrl       vendor API base URL
     * @param apiKey        vendor API key (never logged)
     * @param apiSecret     vendor API secret (never logged)
     * @param webhookSecret shared secret for verifying inbound webhook signatures (never logged)
     */
    public record Endpoint(boolean enabled, String baseUrl, String apiKey, String apiSecret, String webhookSecret) {
    }
}
