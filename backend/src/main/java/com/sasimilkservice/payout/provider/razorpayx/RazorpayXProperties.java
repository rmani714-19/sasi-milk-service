package com.sasimilkservice.payout.provider.razorpayx;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code sasi.payout.razorpayx.*}. Real values come from the environment /
 * a secret manager — never committed. When {@code enabled} is false the
 * {@link RazorpayXPayoutProvider} bean is not registered and the payout engine
 * falls back to the MANUAL provider.
 *
 * @param enabled        register the RazorpayX provider bean
 * @param keyId          RazorpayX API key id
 * @param keySecret      RazorpayX API key secret (never logged)
 * @param accountNumber  the RazorpayX (owner) account number payouts are drawn from
 * @param webhookSecret  shared secret for webhook signature verification (never logged)
 * @param baseUrl        API base, defaults to Razorpay production host
 */
@ConfigurationProperties(prefix = "sasi.payout.razorpayx")
public record RazorpayXProperties(
        boolean enabled,
        String keyId,
        String keySecret,
        String accountNumber,
        String webhookSecret,
        String baseUrl) {

    public RazorpayXProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.razorpay.com/v1";
        }
    }
}
