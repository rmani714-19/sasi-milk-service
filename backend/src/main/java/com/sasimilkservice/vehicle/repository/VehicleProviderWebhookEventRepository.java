package com.sasimilkservice.vehicle.repository;

import com.sasimilkservice.vehicle.domain.VehicleProviderWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleProviderWebhookEventRepository extends JpaRepository<VehicleProviderWebhookEvent, Long> {

    Optional<VehicleProviderWebhookEvent> findByProviderAndEventId(String provider, String eventId);
}
