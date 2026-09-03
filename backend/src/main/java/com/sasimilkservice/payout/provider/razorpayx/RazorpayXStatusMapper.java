package com.sasimilkservice.payout.provider.razorpayx;

import com.sasimilkservice.payout.provider.PayoutProvider.ProviderStatus;

/** Maps RazorpayX payout entity {@code status} strings to the neutral {@link ProviderStatus}. */
final class RazorpayXStatusMapper {

    private RazorpayXStatusMapper() {}

    static ProviderStatus map(String razorpayStatus) {
        if (razorpayStatus == null) {
            return ProviderStatus.PENDING;
        }
        return switch (razorpayStatus.trim().toLowerCase()) {
            case "queued", "pending", "scheduled", "created" -> ProviderStatus.PENDING;
            case "processing", "initiated" -> ProviderStatus.PROCESSING;
            case "processed" -> ProviderStatus.SUCCESS;
            case "reversed" -> ProviderStatus.REVERSED;
            case "cancelled", "rejected", "failed" -> ProviderStatus.FAILED;
            // an unknown status is never optimistically treated as success
            default -> ProviderStatus.PROCESSING;
        };
    }
}
