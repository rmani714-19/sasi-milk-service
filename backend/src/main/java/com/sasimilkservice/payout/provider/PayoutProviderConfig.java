package com.sasimilkservice.payout.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.UUID;

/**
 * Registers the default {@link PayoutProvider}. In environments without a real
 * payout integration this records the disbursement intent and leaves the payout
 * in {@code PROCESSING}; an operator finishes it via the manual mark-paid /
 * mark-failed endpoints. A real provider (e.g. RazorpayX) supplies its own
 * {@code PayoutProvider} bean and this one backs off.
 */
@Configuration
public class PayoutProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sasi.payout.razorpayx", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    @ConditionalOnMissingBean(PayoutProvider.class)
    PayoutProvider manualPayoutProvider() {
        return new PayoutProvider() {

            @Override
            public String name() {
                return "MANUAL";
            }

            @Override
            public CreateResult create(CreateCommand command) {
                String id = "MANUAL-" + UUID.randomUUID();
                return new CreateResult(id, command.idempotencyKey(),
                        ProviderStatus.PROCESSING, "manual provider: awaiting operator confirmation");
            }

            @Override
            public ProviderStatus fetchStatus(String providerPayoutId) {
                return ProviderStatus.PROCESSING;
            }

            @Override
            public WebhookResult parseWebhook(String rawBody, Map<String, String> headers) {
                return new WebhookResult(null, false, null, null, ProviderStatus.PROCESSING,
                        null, null, "manual provider has no webhook", null);
            }
        };
    }
}
