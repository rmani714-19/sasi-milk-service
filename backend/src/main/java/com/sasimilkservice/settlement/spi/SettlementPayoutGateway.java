package com.sasimilkservice.settlement.spi;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published SPI of the {@code settlement} module for the payout flow: read an
 * approved settlement's payable lines and report payment progress back so the
 * settlement status can advance (PAYOUT_INITIATED → PARTIALLY_PAID → PAID/FAILED).
 */
public interface SettlementPayoutGateway {

    /** @throws com.sasimilkservice.common.error.BusinessRuleException if not APPROVED */
    ApprovedSettlement requireApprovedForPayout(long settlementId);

    /**
     * Record the paperwork for handing the batch to the bank (date, optional bank reference).
     * No money moves — the owner pays through the bank and confirms each line manually.
     */
    void recordBankSubmission(long settlementId, java.time.LocalDate submittedOn, String reference, String note);

    void markPayoutInitiated(long settlementId);

    void updateItemPaymentStatus(long settlementItemId, ItemPaymentStatus status, String reference);

    /** Recompute the settlement status from its items' payment statuses. */
    void refreshSettlementPaymentStatus(long settlementId);

    enum ItemPaymentStatus { PENDING, INITIATED, PROCESSING, SUCCESS, FAILED, ON_HOLD, CANCELLED }

    record ApprovedSettlement(long id, String settlementNumber, List<PayableItem> items) {
    }

    record PayableItem(
            long settlementItemId,
            long producerId,
            Long regionId,
            BigDecimal netPayable,
            ItemPaymentStatus paymentStatus) {
    }
}
