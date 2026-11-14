package com.sasimilkservice.settlement.api.dto;

import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementItem;

/**
 * The payment-module status names used in reports and by users, derived from the stored workflow states.
 * Batch: DRAFT, CALCULATED, UNDER_REVIEW, APPROVED, READY_FOR_BANK, SUBMITTED_TO_BANK, PARTIALLY_CONFIRMED,
 * COMPLETED, CANCELLED. Producer line: PENDING, APPROVED, SUBMITTED_TO_BANK, PROCESSING, PAID, FAILED, CANCELLED.
 * (The stored states keep their names so existing records and workflow rules are untouched.)
 */
public final class SpecStatus {

    private SpecStatus() {}

    public static String batch(Settlement.Status s) {
        return switch (s) {
            case DRAFT -> "DRAFT";
            case CALCULATED -> "CALCULATED";
            case REVIEWED, UNDER_VERIFICATION, VERIFIED -> "UNDER_REVIEW";
            case APPROVED -> "READY_FOR_BANK";
            case PAYOUT_INITIATED, FAILED -> "SUBMITTED_TO_BANK";
            case PARTIALLY_PAID -> "PARTIALLY_CONFIRMED";
            case PAID -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
        };
    }

    public static String line(SettlementItem.PaymentStatus p, Settlement.Status batch) {
        return switch (p) {
            case PENDING -> batch == Settlement.Status.APPROVED ? "APPROVED" : "PENDING";
            case INITIATED -> "SUBMITTED_TO_BANK";
            case PROCESSING -> "PROCESSING";
            case SUCCESS -> "PAID";
            case FAILED -> "FAILED";
            case ON_HOLD -> "PENDING";
            case CANCELLED -> "CANCELLED";
        };
    }
}
