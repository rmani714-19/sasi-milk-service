package com.sasimilkservice.payout.repository;

import com.sasimilkservice.payout.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByProviderAndEventId(String provider, String eventId);

    boolean existsByProviderAndEventIdAndProcessingStatus(String provider, String eventId, String processingStatus);
}
