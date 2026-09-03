package com.sasimilkservice.payout.provider.razorpayx;

import java.util.Set;

/**
 * Maps RazorpayX error codes / descriptions to a stable internal failure code and
 * a "retryable" flag. Retryable errors are transient (rate limits, gateway
 * hiccups); a non-retryable error (bad account, KYC) must not be auto-retried
 * with the same idempotency key.
 */
public final class RazorpayXErrorMapper {

    private RazorpayXErrorMapper() {}

    private static final Set<String> RETRYABLE_CODES = Set.of(
            "SERVER_ERROR", "GATEWAY_ERROR", "BAD_REQUEST_RATE_LIMIT_EXCEEDED");

    private static final Set<Integer> RETRYABLE_HTTP = Set.of(429, 500, 502, 503, 504);

    public record MappedError(String failureCode, String failureReason, boolean retryable) {
    }

    public static MappedError map(int httpStatus, String code, String description) {
        String c = code == null || code.isBlank() ? "PROVIDER_ERROR" : code.trim();
        String reason = description == null || description.isBlank()
                ? "RazorpayX error " + httpStatus
                : description.trim();

        boolean retryable = RETRYABLE_HTTP.contains(httpStatus) || RETRYABLE_CODES.contains(c);

        String failureCode = switch (c) {
            case "BAD_REQUEST_ERROR" -> classifyBadRequest(reason);
            case "GATEWAY_ERROR" -> "PROVIDER_GATEWAY_ERROR";
            case "SERVER_ERROR" -> "PROVIDER_SERVER_ERROR";
            default -> c;
        };
        return new MappedError(failureCode, reason, retryable);
    }

    private static String classifyBadRequest(String reason) {
        String r = reason.toLowerCase();
        if (r.contains("ifsc")) {
            return "INVALID_IFSC";
        }
        if (r.contains("account number") || r.contains("account_number")) {
            return "INVALID_ACCOUNT_NUMBER";
        }
        if (r.contains("insufficient") && r.contains("balance")) {
            return "INSUFFICIENT_BALANCE";
        }
        if (r.contains("beneficiary") || r.contains("fund account")) {
            return "INVALID_BENEFICIARY";
        }
        return "BAD_REQUEST";
    }
}
