package com.sasimilkservice.vehicle.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.vehicle.domain.IgnitionStatus;
import com.sasimilkservice.vehicle.domain.MotionStatus;
import com.sasimilkservice.vehicle.domain.VehicleDevice;
import com.sasimilkservice.vehicle.domain.VehicleProviderWebhookEvent;
import com.sasimilkservice.vehicle.provider.VehicleProviderProperties;
import com.sasimilkservice.vehicle.repository.VehicleDeviceRepository;
import com.sasimilkservice.vehicle.repository.VehicleProviderWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Applies an inbound provider webhook to the tracking tables. Dedupes on
 * {@code (provider, event_id)} exactly like the payout module's webhook log —
 * a redelivery is recognised and ignored rather than reprocessed.
 */
@Service
@Transactional(readOnly = true)
public class VehicleWebhookService {

    private final VehicleProviderWebhookEventRepository events;
    private final VehicleDeviceRepository devices;
    private final VehicleTrackingService tracking;
    private final VehicleProviderProperties props;
    private final ObjectMapper mapper;

    public VehicleWebhookService(VehicleProviderWebhookEventRepository events, VehicleDeviceRepository devices,
                                 VehicleTrackingService tracking, VehicleProviderProperties props,
                                 ObjectMapper mapper) {
        this.events = events;
        this.devices = devices;
        this.tracking = tracking;
        this.props = props;
        this.mapper = mapper;
    }

    @Transactional
    public void process(String provider, String rawBody, String signatureHeader) {
        verifySignature(rawBody, signatureHeader);

        VehicleWebhookEnvelope env;
        Map<String, Object> payload;
        try {
            env = mapper.readValue(rawBody, VehicleWebhookEnvelope.class);
            payload = mapper.readValue(rawBody, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new BusinessRuleException("Malformed webhook payload");
        }

        // Checked up front rather than caught from the insert: catching a constraint
        // violation this late would leave the surrounding transaction marked
        // rollback-only, and returning normally afterwards would then fail with
        // UnexpectedRollbackException instead of the quiet no-op a redelivery should be.
        if (env.eventId() != null && !env.eventId().isBlank()
                && events.findByProviderAndEventId(provider, env.eventId()).isPresent()) {
            return;
        }

        VehicleProviderWebhookEvent evt = new VehicleProviderWebhookEvent();
        evt.setProvider(provider);
        evt.setEventId(env.eventId());
        evt.setEventType(env.eventType());
        evt.setPayload(payload);
        events.saveAndFlush(evt);

        try {
            apply(provider, env);
            evt.setStatus(VehicleProviderWebhookEvent.Status.PROCESSED);
        } catch (RuntimeException e) {
            evt.setStatus(VehicleProviderWebhookEvent.Status.FAILED);
            evt.setErrorDetail(e.getMessage());
            throw e;
        }
    }

    private void apply(String provider, VehicleWebhookEnvelope env) {
        if (env.externalDeviceId() == null || env.externalDeviceId().isBlank()) {
            throw new BusinessRuleException("externalDeviceId is required");
        }
        VehicleDevice device = devices.findByProviderAndExternalDeviceId(provider, env.externalDeviceId())
                .orElseThrow(() -> new BusinessRuleException(
                        "No device registered for provider '" + provider + "' / device '" + env.externalDeviceId() + "'"));

        if (device.getVehicleId() == null) {
            return; // recorded above; nothing to attach this fix to yet
        }
        if (env.latitude() == null || env.longitude() == null) {
            return; // a non-location event (e.g. a status ping) — device.lastSeenAt already touched via ingest path below
        }

        tracking.ingest(device.getVehicleId(), device.getId(), env.timestamp(), env.latitude(), env.longitude(),
                env.speedKmph(), env.directionDegrees(), env.altitude(),
                parseEnum(IgnitionStatus.class, env.ignitionStatus(), IgnitionStatus.UNKNOWN),
                parseEnum(MotionStatus.class, env.motionStatus(), MotionStatus.UNKNOWN),
                env.gpsAccuracy(), env.raw());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * HMAC-SHA256 over the raw body, constant-time compared. No real vendor is
     * connected yet, so when no webhook secret is configured (dev/mock) the
     * check is skipped rather than rejecting every call outright; production
     * must set {@code sasi.vehicle.provider.tracking.webhook-secret}.
     */
    private void verifySignature(String rawBody, String signatureHeader) {
        String secret = props.tracking().webhookSecret();
        if (secret == null || secret.isBlank()) {
            return;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BusinessRuleException("Missing webhook signature");
        }
        String expected = hmacSha256Hex(secret, rawBody);
        if (!constantTimeEquals(expected, signatureHeader.trim())) {
            throw new BusinessRuleException("Invalid webhook signature");
        }
    }

    private static String hmacSha256Hex(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute webhook signature", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
