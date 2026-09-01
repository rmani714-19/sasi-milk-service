package com.sasimilkservice.payout.provider;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Abstraction over a payout/disbursement provider (RazorpayX, bank file, manual…).
 * Implementations must be idempotent on {@link CreateCommand#idempotencyKey()} —
 * a network timeout on {@link #create} is never treated as failure without a
 * follow-up {@link #fetchStatus} or webhook (spec payout.critical_rules).
 */
public interface PayoutProvider {

    /** Provider code stored on {@code payouts.provider}. */
    String name();

    CreateResult create(CreateCommand command);

    /** Poll current status for reconciliation. */
    ProviderStatus fetchStatus(String providerPayoutId);

    /** Parse + signature-verify an inbound webhook body. */
    WebhookResult parseWebhook(String rawBody, Map<String, String> headers);

    enum ProviderStatus { PENDING, PROCESSING, SUCCESS, FAILED }

    record CreateCommand(
            String idempotencyKey,
            BigDecimal amount,
            String currency,
            String accountHolderName,
            String accountNumber,
            String ifsc,
            String bankName,
            String reference) {
    }

    record CreateResult(
            String providerPayoutId,
            String providerRef,
            ProviderStatus status,
            String rawResponse) {
    }

    record WebhookResult(
            boolean signatureValid,
            String providerPayoutId,
            String providerRef,
            ProviderStatus status,
            String utr,
            String failureCode,
            String failureReason) {
    }
}
