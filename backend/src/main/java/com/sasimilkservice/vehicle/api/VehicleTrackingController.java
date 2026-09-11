package com.sasimilkservice.vehicle.api;

import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.IngestLocationRequest;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.LocationResponse;
import com.sasimilkservice.vehicle.api.dto.VehicleDtos.TripResponse;
import com.sasimilkservice.vehicle.service.VehicleTrackingService;
import com.sasimilkservice.vehicle.spi.VehicleStatusQuery.VehicleStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Live location, history and trips. All reads come from vehicle_latest_locations
 * / vehicle_location_history / vehicle_trips — never a live call to a vendor —
 * so the UI never blocks on (or fails because of) a provider outage.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleTrackingController {

    private static final String VIEW_LOCATION = "hasAnyAuthority('VIEW_LIVE_LOCATION','VIEW_DASHBOARD','ALL')";
    private static final String VIEW_TRIPS = "hasAnyAuthority('VIEW_TRIP_HISTORY','ALL')";
    private static final String INGEST = "hasAuthority('ALL')"; // manual/testing ingest — not a normal user action

    private final VehicleTrackingService service;

    public VehicleTrackingController(VehicleTrackingService service) {
        this.service = service;
    }

    /** Kept for the dashboard's existing "vehicles online" card — every vehicle at once. */
    @GetMapping("/status")
    @PreAuthorize(VIEW_LOCATION)
    public List<VehicleStatus> statusAll() {
        return service.allStatuses();
    }

    @GetMapping("/{vehicleId}/status")
    @PreAuthorize(VIEW_LOCATION)
    public VehicleStatus status(@PathVariable long vehicleId) {
        return service.statusOf(vehicleId);
    }

    @GetMapping("/{vehicleId}/location")
    @PreAuthorize(VIEW_LOCATION)
    public LocationResponse location(@PathVariable long vehicleId) {
        return service.latestLocation(vehicleId).map(LocationResponse::from)
                .orElseThrow(() -> new NotFoundException("No location has been received yet for vehicle " + vehicleId));
    }

    @GetMapping("/{vehicleId}/location/history")
    @PreAuthorize(VIEW_LOCATION)
    public List<LocationResponse> locationHistory(
            @PathVariable long vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "200") int limit) {
        return service.locationHistory(vehicleId, from, to, limit).stream().map(LocationResponse::from).toList();
    }

    @GetMapping("/{vehicleId}/trips")
    @PreAuthorize(VIEW_TRIPS)
    public List<TripResponse> trips(
            @PathVariable long vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.trips(vehicleId, from, to).stream().map(TripResponse::from).toList();
    }

    /**
     * Manual telemetry ingest for development/testing without real hardware —
     * exactly the call a real provider adapter (or its webhook handler) would
     * make internally. Restricted to ALL; day-to-day ingestion is expected to
     * arrive via {@code POST /vehicles/providers/{provider}/webhook}.
     */
    @PostMapping("/{vehicleId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(INGEST)
    public void ingest(@PathVariable long vehicleId, @Valid @RequestBody IngestLocationRequest r) {
        service.ingest(vehicleId, r.deviceId(), r.providerTimestamp(), r.latitude(), r.longitude(), r.speedKmph(),
                r.directionDegrees(), r.altitude(), r.ignitionStatus(), r.motionStatus(), r.gpsAccuracy(), null);
    }
}
