package com.sasimilkservice.vehicle.provider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Abstraction over a live-camera / dashcam / MDVR vendor. Never returns a
 * fabricated stream URL — if the provider has nothing to offer (not
 * connected, camera offline, feature unsupported) it returns an empty
 * result and the UI shows "camera offline" rather than a broken player.
 *
 * <p>Prefer short-lived, signed playback URLs when the provider supports
 * them; {@code expiresAt} on {@link LiveStreamInfo}/{@link PlaybackInfo}
 * lets the frontend know when to re-request a fresh one. Provider API keys
 * never appear in any of these results — they stay in server-side
 * configuration.
 */
public interface VehicleCameraProvider {

    String name();

    CameraStatus getCameraStatus(String providerCameraId);

    Optional<LiveStreamInfo> getLiveStream(String providerCameraId);

    Optional<PlaybackInfo> getPlayback(String providerCameraId, Instant from, Instant to);

    List<RecordingSummary> getRecordingList(String providerCameraId, Instant from, Instant to);

    record CameraStatus(boolean online, Instant lastSeenAt) {
    }

    /** {@code protocol} is whatever the vendor actually speaks — e.g. HLS, WEBRTC, RTSP, PROPRIETARY. */
    record LiveStreamInfo(String protocol, String url, Instant expiresAt) {
    }

    record PlaybackInfo(String protocol, String url, Instant expiresAt) {
    }

    record RecordingSummary(String recordingId, Instant startedAt, Instant endedAt, String protocol) {
    }
}
