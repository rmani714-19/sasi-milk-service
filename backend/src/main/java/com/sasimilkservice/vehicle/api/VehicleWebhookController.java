package com.sasimilkservice.vehicle.api;

import com.sasimilkservice.vehicle.service.VehicleWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound GPS/camera provider webhook — authenticated by signature inside
 * {@link VehicleWebhookService}, not by JWT (see SecurityConfig's PUBLIC
 * allowlist). {@code {provider}} lets more than one vendor be connected at
 * once without changing the URL shape.
 */
@RestController
@RequestMapping("/api/v1/vehicles/providers")
public class VehicleWebhookController {

    private final VehicleWebhookService service;

    public VehicleWebhookController(VehicleWebhookService service) {
        this.service = service;
    }

    @PostMapping("/{provider}/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@PathVariable String provider, @RequestBody(required = false) String body,
                        @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        service.process(provider, body == null ? "" : body, signature);
    }
}
