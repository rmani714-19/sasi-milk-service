package com.sasimilkservice.vehicle.api;

import com.sasimilkservice.vehicle.api.dto.VehicleDtos.AlertResponse;
import com.sasimilkservice.vehicle.domain.VehicleAlert;
import com.sasimilkservice.vehicle.service.VehicleAlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles/alerts")
public class VehicleAlertController {

    private static final String VIEW = "hasAnyAuthority('VIEW_ALERTS','ALL')";
    private static final String ACK = "hasAnyAuthority('VIEW_ALERTS','MANAGE_VEHICLES','ALL')";

    private final VehicleAlertService service;

    public VehicleAlertController(VehicleAlertService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public List<AlertResponse> list(@RequestParam(required = false) Long vehicleId,
                                    @RequestParam(required = false) VehicleAlert.Status status) {
        return service.list(vehicleId, status).stream().map(AlertResponse::from).toList();
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize(ACK)
    public AlertResponse acknowledge(@PathVariable long alertId) {
        return AlertResponse.from(service.acknowledge(alertId));
    }
}
