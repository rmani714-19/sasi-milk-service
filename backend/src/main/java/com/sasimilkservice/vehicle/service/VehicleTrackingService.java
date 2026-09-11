package com.sasimilkservice.vehicle.service;

import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.vehicle.domain.IgnitionStatus;
import com.sasimilkservice.vehicle.domain.MotionStatus;
import com.sasimilkservice.vehicle.domain.Vehicle;
import com.sasimilkservice.vehicle.domain.VehicleLatestLocation;
import com.sasimilkservice.vehicle.domain.VehicleLocationHistory;
import com.sasimilkservice.vehicle.domain.VehicleTrip;
import com.sasimilkservice.vehicle.repository.VehicleDeviceRepository;
import com.sasimilkservice.vehicle.repository.VehicleLatestLocationRepository;
import com.sasimilkservice.vehicle.repository.VehicleLocationHistoryRepository;
import com.sasimilkservice.vehicle.repository.VehicleRepository;
import com.sasimilkservice.vehicle.repository.VehicleTripRepository;
import com.sasimilkservice.vehicle.spi.VehicleStatusQuery;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ingests telemetry (from the provider webhook or a manual/testing endpoint)
 * into the fast "latest" row and the append-only history, derives
 * online/offline status, and runs a deliberately simple trip detector.
 *
 * <p>Trip {@code distance_km} is the straight-line (haversine) distance
 * between the trip's start and end fix, not an integrated path length —
 * a reasonable approximation for short collection routes. Summing
 * consecutive-fix distances from the history table is a natural future
 * enhancement if more accuracy is needed.
 */
@Service
@Transactional(readOnly = true)
public class VehicleTrackingService implements VehicleStatusQuery {

    private final VehicleRepository vehicles;
    private final VehicleDeviceRepository devices;
    private final VehicleLatestLocationRepository latest;
    private final VehicleLocationHistoryRepository history;
    private final VehicleTripRepository trips;
    private final VehicleTrackingProperties props;
    private final VehicleAlertService alerts;

    public VehicleTrackingService(VehicleRepository vehicles, VehicleDeviceRepository devices,
                                  VehicleLatestLocationRepository latest, VehicleLocationHistoryRepository history,
                                  VehicleTripRepository trips, VehicleTrackingProperties props,
                                  VehicleAlertService alerts) {
        this.vehicles = vehicles;
        this.devices = devices;
        this.latest = latest;
        this.history = history;
        this.trips = trips;
        this.props = props;
        this.alerts = alerts;
    }

    public Optional<VehicleLatestLocation> latestLocation(long vehicleId) {
        return latest.findByVehicleId(vehicleId);
    }

    public List<VehicleLocationHistory> locationHistory(long vehicleId, Instant from, Instant to, int limit) {
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw new BusinessRuleException("'to' must not be before 'from'");
            }
            return history.findByVehicleIdAndProviderTimestampBetweenOrderByProviderTimestampAsc(vehicleId, from, to);
        }
        return history.findByVehicleIdOrderByProviderTimestampDesc(vehicleId, Limit.of(Math.min(Math.max(limit, 1), 2000)));
    }

    public List<VehicleTrip> trips(long vehicleId, Instant from, Instant to) {
        if (from != null && to != null) {
            return trips.findByVehicleIdAndStartedAtBetweenOrderByStartedAtDesc(vehicleId, from, to);
        }
        return trips.findByVehicleIdOrderByStartedAtDesc(vehicleId);
    }

    @Override
    public List<VehicleStatus> allStatuses() {
        Instant cutoff = Instant.now().minus(props.onlineWindow());
        return vehicles.findAll().stream().map(v -> toStatus(v, cutoff)).toList();
    }

    public VehicleStatus statusOf(long vehicleId) {
        Vehicle v = vehicles.findById(vehicleId).orElseThrow(() -> NotFoundException.of("Vehicle", vehicleId));
        return toStatus(v, Instant.now().minus(props.onlineWindow()));
    }

    private VehicleStatus toStatus(Vehicle v, Instant cutoff) {
        VehicleLatestLocation last = latest.findByVehicleId(v.getId()).orElse(null);
        boolean online = last != null && last.getProviderTimestamp().isAfter(cutoff);
        return new VehicleStatus(
                v.getId(), v.getCode(), v.getRegistrationNumber(), online,
                last == null ? null : last.getLatitude().doubleValue(),
                last == null ? null : last.getLongitude().doubleValue(),
                last == null || last.getSpeedKmph() == null ? null : last.getSpeedKmph().doubleValue(),
                last == null ? null : last.getProviderTimestamp());
    }

    /**
     * Record one telemetry fix. Out-of-order delivery is handled safely: the
     * history row is always appended, but the "latest" row is only
     * overwritten when this fix is not older than what's already recorded.
     */
    @Transactional
    public void ingest(long vehicleId, Long deviceId, Instant providerTimestamp, BigDecimal lat, BigDecimal lng,
                       BigDecimal speedKmph, BigDecimal directionDegrees, BigDecimal altitude,
                       IgnitionStatus ignitionStatus, MotionStatus motionStatus, BigDecimal gpsAccuracy,
                       Map<String, Object> raw) {
        if (!vehicles.existsById(vehicleId)) {
            throw NotFoundException.of("Vehicle", vehicleId);
        }
        validateCoordinates(lat, lng);
        Instant ts = providerTimestamp == null ? Instant.now() : providerTimestamp;

        VehicleLocationHistory h = new VehicleLocationHistory();
        h.setVehicleId(vehicleId);
        h.setDeviceId(deviceId);
        h.setProviderTimestamp(ts);
        h.setLatitude(lat);
        h.setLongitude(lng);
        h.setSpeedKmph(speedKmph);
        h.setDirectionDegrees(directionDegrees);
        h.setAltitude(altitude);
        h.setIgnitionStatus(ignitionStatus);
        h.setMotionStatus(motionStatus);
        h.setGpsAccuracy(gpsAccuracy);
        h.setRaw(raw);
        history.save(h);

        VehicleLatestLocation existing = latest.findByVehicleId(vehicleId).orElse(null);
        if (existing == null || !ts.isBefore(existing.getProviderTimestamp())) {
            VehicleLatestLocation l = existing == null ? new VehicleLatestLocation() : existing;
            l.setVehicleId(vehicleId);
            l.setDeviceId(deviceId);
            l.setLatitude(lat);
            l.setLongitude(lng);
            l.setSpeedKmph(speedKmph);
            l.setDirectionDegrees(directionDegrees);
            l.setAltitude(altitude);
            l.setIgnitionStatus(ignitionStatus);
            l.setMotionStatus(motionStatus);
            l.setGpsAccuracy(gpsAccuracy);
            l.setProviderTimestamp(ts);
            l.setReceivedAt(Instant.now());
            latest.save(l);
        }

        if (deviceId != null) {
            devices.findById(deviceId).ifPresent(d -> d.setLastSeenAt(Instant.now()));
        }

        alerts.evaluateOnFix(vehicleId, deviceId, ts, lat, lng, speedKmph, ignitionStatus);
        updateTrip(vehicleId, deviceId, ts, lat, lng, speedKmph, motionStatus);
    }

    static void validateCoordinates(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            throw new BusinessRuleException("Latitude and longitude are required");
        }
        if (lat.compareTo(BigDecimal.valueOf(-90)) < 0 || lat.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BusinessRuleException("Latitude " + lat + " is out of range");
        }
        if (lng.compareTo(BigDecimal.valueOf(-180)) < 0 || lng.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BusinessRuleException("Longitude " + lng + " is out of range");
        }
    }

    private void updateTrip(long vehicleId, Long deviceId, Instant ts, BigDecimal lat, BigDecimal lng,
                            BigDecimal speedKmph, MotionStatus motionStatus) {
        boolean movingNow = motionStatus == MotionStatus.MOVING
                || (motionStatus != MotionStatus.STOPPED && motionStatus != MotionStatus.IDLE
                    && speedKmph != null && speedKmph.compareTo(props.trip().movingSpeedThresholdKmph()) >= 0);

        Optional<VehicleTrip> ongoing = trips.findByVehicleIdAndStatus(vehicleId, VehicleTrip.Status.ONGOING);

        if (movingNow && ongoing.isEmpty()) {
            VehicleTrip t = new VehicleTrip();
            t.setVehicleId(vehicleId);
            t.setDeviceId(deviceId);
            vehicles.findById(vehicleId).ifPresent(v -> t.setDriverId(v.getDriverId()));
            t.setStartedAt(ts);
            t.setStartLatitude(lat);
            t.setStartLongitude(lng);
            t.setMaxSpeedKmph(speedKmph);
            t.setStatus(VehicleTrip.Status.ONGOING);
            trips.save(t);
        } else if (movingNow) {
            VehicleTrip t = ongoing.get();
            if (speedKmph != null && (t.getMaxSpeedKmph() == null || speedKmph.compareTo(t.getMaxSpeedKmph()) > 0)) {
                t.setMaxSpeedKmph(speedKmph);
            }
        } else if (ongoing.isPresent()) {
            VehicleTrip t = ongoing.get();
            Duration duration = Duration.between(t.getStartedAt(), ts);
            if (duration.compareTo(props.trip().minDuration()) < 0) {
                // too short to be a real trip — discard as noise rather than saving it
                trips.delete(t);
                return;
            }
            t.setEndedAt(ts);
            t.setEndLatitude(lat);
            t.setEndLongitude(lng);
            t.setDurationSeconds(duration.getSeconds());
            BigDecimal distanceKm = haversineKm(t.getStartLatitude(), t.getStartLongitude(), lat, lng);
            t.setDistanceKm(distanceKm);
            if (duration.getSeconds() > 0 && distanceKm != null) {
                BigDecimal hours = BigDecimal.valueOf(duration.getSeconds())
                        .divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
                if (hours.signum() > 0) {
                    t.setAverageSpeedKmph(distanceKm.divide(hours, 2, RoundingMode.HALF_UP));
                }
            }
            t.setStatus(VehicleTrip.Status.COMPLETED);
        }
    }

    private static BigDecimal haversineKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double r = 6371.0;
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double dLambda = Math.toRadians(lon2.subtract(lon1).doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(r * c).setScale(3, RoundingMode.HALF_UP);
    }
}
