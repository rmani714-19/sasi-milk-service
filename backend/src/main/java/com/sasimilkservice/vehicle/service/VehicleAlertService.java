package com.sasimilkservice.vehicle.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.vehicle.domain.IgnitionStatus;
import com.sasimilkservice.vehicle.domain.Vehicle;
import com.sasimilkservice.vehicle.domain.VehicleAlert;
import com.sasimilkservice.vehicle.domain.VehicleDevice;
import com.sasimilkservice.vehicle.repository.VehicleAlertRepository;
import com.sasimilkservice.vehicle.repository.VehicleDeviceRepository;
import com.sasimilkservice.vehicle.repository.VehicleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Alerts are only ever raised from real telemetry/provider data — never
 * invented. At most one OPEN alert exists per (vehicle, type); a fix or
 * sweep that finds the condition no longer true auto-clears it instead of
 * leaving it open forever.
 */
@Service
@Transactional(readOnly = true)
public class VehicleAlertService {

    private final VehicleAlertRepository alerts;
    private final VehicleRepository vehicles;
    private final VehicleDeviceRepository devices;
    private final VehicleTrackingProperties props;
    private final AuditRecorder audit;

    public VehicleAlertService(VehicleAlertRepository alerts, VehicleRepository vehicles,
                               VehicleDeviceRepository devices, VehicleTrackingProperties props,
                               AuditRecorder audit) {
        this.alerts = alerts;
        this.vehicles = vehicles;
        this.devices = devices;
        this.props = props;
        this.audit = audit;
    }

    public List<VehicleAlert> list(Long vehicleId, VehicleAlert.Status status) {
        if (vehicleId != null) {
            return alerts.findByVehicleIdOrderByOccurredAtDesc(vehicleId);
        }
        if (status != null) {
            return alerts.findByStatusOrderByOccurredAtDesc(status);
        }
        return alerts.findAllByOrderByOccurredAtDesc();
    }

    @Transactional
    public VehicleAlert acknowledge(long id) {
        VehicleAlert a = alerts.findById(id).orElseThrow(() -> NotFoundException.of("VehicleAlert", id));
        if (a.getStatus() == VehicleAlert.Status.ACKNOWLEDGED) {
            throw new BusinessRuleException("This alert is already acknowledged");
        }
        a.setStatus(VehicleAlert.Status.ACKNOWLEDGED);
        a.setAcknowledgedAt(Instant.now());
        CurrentUser.id().ifPresent(a::setAcknowledgedBy);
        audit.record("VEHICLE_ALERT_ACKNOWLEDGED", "VehicleAlert", id,
                a.getAlertType() + " on vehicle " + a.getVehicleId() + " acknowledged");
        return a;
    }

    @Transactional
    public void raise(long vehicleId, Long deviceId, Long driverId, VehicleAlert.AlertType type,
                      VehicleAlert.Severity severity, String message, BigDecimal lat, BigDecimal lng,
                      BigDecimal speedKmph, Instant occurredAt) {
        if (alerts.findByVehicleIdAndAlertTypeAndStatus(vehicleId, type, VehicleAlert.Status.OPEN).isPresent()) {
            return; // a continuing condition — don't spam a new row every time
        }
        VehicleAlert a = new VehicleAlert();
        a.setVehicleId(vehicleId);
        a.setDeviceId(deviceId);
        a.setDriverId(driverId);
        a.setAlertType(type);
        a.setSeverity(severity);
        a.setMessage(message);
        a.setLatitude(lat);
        a.setLongitude(lng);
        a.setSpeedKmph(speedKmph);
        a.setOccurredAt(occurredAt == null ? Instant.now() : occurredAt);
        // Checked-then-inserted rather than caught from a constraint violation: catching
        // it this late would mark the transaction rollback-only and turn a quiet
        // "already open" no-op into an UnexpectedRollbackException on return. The
        // find-then-save above already closes all but a vanishingly small race window
        // (two ingests for the same vehicle/type landing at the same instant).
        alerts.save(a);
        audit.record("VEHICLE_ALERT_RAISED", "Vehicle", vehicleId, type + ": " + message);
    }

    /** Clear an OPEN alert once its condition is no longer true. A no-op if nothing is open. */
    @Transactional
    public void autoClear(long vehicleId, VehicleAlert.AlertType type) {
        alerts.findByVehicleIdAndAlertTypeAndStatus(vehicleId, type, VehicleAlert.Status.OPEN).ifPresent(a -> {
            a.setStatus(VehicleAlert.Status.ACKNOWLEDGED);
            a.setAcknowledgedAt(Instant.now());
            audit.record("VEHICLE_ALERT_CLEARED", "Vehicle", vehicleId, type + " condition cleared");
        });
    }

    /** Called from VehicleTrackingService on every ingested fix. */
    @Transactional
    public void evaluateOnFix(long vehicleId, Long deviceId, Instant ts, BigDecimal lat, BigDecimal lng,
                              BigDecimal speedKmph, IgnitionStatus ignition) {
        if (speedKmph != null && speedKmph.compareTo(props.overspeedThresholdKmph()) >= 0) {
            raise(vehicleId, deviceId, currentDriverId(vehicleId), VehicleAlert.AlertType.OVERSPEED,
                    VehicleAlert.Severity.WARNING,
                    "Speed " + speedKmph + " km/h exceeds the " + props.overspeedThresholdKmph() + " km/h limit",
                    lat, lng, speedKmph, ts);
        } else {
            autoClear(vehicleId, VehicleAlert.AlertType.OVERSPEED);
        }
        // any fix means the device is alive — clear a stale DEVICE_OFFLINE alert
        autoClear(vehicleId, VehicleAlert.AlertType.DEVICE_OFFLINE);
    }

    private Long currentDriverId(long vehicleId) {
        return vehicles.findById(vehicleId).map(Vehicle::getDriverId).orElse(null);
    }

    /** Any ACTIVE, attached device with no fix inside the offline window raises DEVICE_OFFLINE. */
    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void sweepOfflineDevices() {
        Instant cutoff = Instant.now().minus(props.deviceOfflineCheckWindow());
        for (VehicleDevice d : devices.findAllByOrderByIdDesc()) {
            if (d.getVehicleId() == null || d.getStatus() != VehicleDevice.Status.ACTIVE) {
                continue;
            }
            boolean offline = d.getLastSeenAt() == null || d.getLastSeenAt().isBefore(cutoff);
            if (offline) {
                raise(d.getVehicleId(), d.getId(), currentDriverId(d.getVehicleId()), VehicleAlert.AlertType.DEVICE_OFFLINE,
                        VehicleAlert.Severity.WARNING,
                        "No signal from this device since " + (d.getLastSeenAt() == null ? "it was installed" : d.getLastSeenAt()),
                        null, null, null, Instant.now());
            }
        }
    }
}
