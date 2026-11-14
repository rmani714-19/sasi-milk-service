package com.sasimilkservice.payout.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.notification.spi.NotificationPublisher;
import com.sasimilkservice.notification.spi.NotificationPublisher.NotificationRequest;
import com.sasimilkservice.payout.domain.Payout;
import com.sasimilkservice.payout.domain.PayoutTransaction;
import com.sasimilkservice.payout.repository.PayoutRepository;
import com.sasimilkservice.payout.repository.PayoutTransactionRepository;
import com.sasimilkservice.settlement.spi.SettlementPayoutGateway;
import com.sasimilkservice.settlement.spi.SettlementPayoutGateway.ApprovedSettlement;
import com.sasimilkservice.settlement.spi.SettlementPayoutGateway.ItemPaymentStatus;
import com.sasimilkservice.settlement.spi.SettlementPayoutGateway.PayableItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Producer payment tracking. The application <b>never moves money</b>: it keeps one payment line per
 * approved settlement item, and the owner pays through the bank and then records the outcome here
 * (confirm with bank reference, or mark failed). Idempotent on {@code po-<settlementItemId>} — unique
 * {@code settlement_item_id} guarantees no duplicate line.
 */
@Service
@Transactional(readOnly = true)
public class PayoutService {

    private final PayoutRepository payouts;
    private final PayoutTransactionRepository payoutTxns;
    private final SettlementPayoutGateway settlements;
    private final NotificationPublisher notifications;
    private final AuditRecorder audit;

    public PayoutService(PayoutRepository payouts,
                         PayoutTransactionRepository payoutTxns,
                         SettlementPayoutGateway settlements,
                         NotificationPublisher notifications,
                         AuditRecorder audit) {
        this.payouts = payouts;
        this.payoutTxns = payoutTxns;
        this.settlements = settlements;
        this.notifications = notifications;
        this.audit = audit;
    }

    public List<Payout> all() {
        return payouts.findAllByOrderByIdDesc();
    }

    public List<Payout> forSettlement(long settlementId) {
        return payouts.findBySettlementIdOrderByProducerIdAsc(settlementId);
    }

    public Payout require(long id) {
        return payouts.findById(id).orElseThrow(() -> NotFoundException.of("Payout", id));
    }

    public Payout requireByPublicId(UUID publicId) {
        return payouts.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Payout " + publicId + " not found"));
    }

    @Transactional
    public List<Payout> initiate(long settlementId) {
        return initiate(settlementId, null, null, null);
    }

    /**
     * Hand an approved batch to the bank: records the submission (date, reference, note) and creates the
     * per-producer payment lines. Nothing is sent anywhere — each line waits for the owner to confirm it
     * after the bank has paid.
     */
    @Transactional
    public List<Payout> initiate(long settlementId, java.time.LocalDate submittedOn, String bankReference, String note) {
        ApprovedSettlement settlement = settlements.requireApprovedForPayout(settlementId);
        settlements.recordBankSubmission(settlementId, submittedOn, bankReference, note);
        List<Payout> result = new java.util.ArrayList<>();

        for (PayableItem item : settlement.items()) {
            if (payouts.findBySettlementItemId(item.settlementItemId()).isPresent()) {
                continue; // already has a payout
            }
            if (MoneyMath.money(item.netPayable()).signum() <= 0) {
                continue; // nothing to pay
            }
            Payout created = createPayout(settlementId, settlement.settlementNumber(), item);
            if (created != null) {
                result.add(created);
            }
        }

        settlements.markPayoutInitiated(settlementId);
        return result;
    }

    /** Recorded on every payment line — the owner pays outside the app. */
    private static final String MANUAL = "MANUAL";

    private Payout createPayout(long settlementId, String settlementNumber, PayableItem item) {
        Payout p = new Payout();
        p.setSettlementId(settlementId);
        p.setSettlementItemId(item.settlementItemId());
        p.setProducerId(item.producerId());
        p.setAmount(MoneyMath.money(item.netPayable()));
        p.setIdempotencyKey("po-" + item.settlementItemId());
        p.setProvider(MANUAL);

        p.setStatus(Payout.Status.PROCESSING); // listed for the bank; waiting for the owner's confirmation
        p.setAttemptCount(1);
        p.setLastAttemptAt(Instant.now());
        p = payouts.save(p);
        logTxn(p.getId(), PayoutTransaction.EventType.REQUEST, "PROCESSING", null, requestPayload(p));

        settlements.updateItemPaymentStatus(item.settlementItemId(),
                toItemStatus(p.getStatus()), null);
        audit.record("PAYOUT_CREATED", "Payout", p.getId(),
                "Payment line %s for producer %d listed for the bank, status %s".formatted(
                        p.getAmount(), item.producerId(), p.getStatus()));
        return p;
    }

    @Transactional
    public Payout retry(long payoutId) {
        Payout p = require(payoutId);
        if (p.getStatus() != Payout.Status.FAILED) {
            throw new BusinessRuleException("Only a FAILED payout can be retried");
        }
        // Put the line back on the bank list; the owner pays again outside the app and confirms.
        p.setAttemptCount(p.getAttemptCount() + 1);
        p.setLastAttemptAt(Instant.now());
        p.setFailureCode(null);
        p.setFailureReason(null);
        p.setStatus(Payout.Status.PROCESSING);
        logTxn(p.getId(), PayoutTransaction.EventType.RETRY, "PROCESSING", null, requestPayload(p));

        settlements.updateItemPaymentStatus(p.getSettlementItemId(), toItemStatus(p.getStatus()), null);
        settlements.refreshSettlementPaymentStatus(p.getSettlementId());
        audit.record("PAYOUT_RETRIED", "Payout", p.getId(), "Payment line put back on the bank list, status " + p.getStatus());
        return p;
    }

    @Transactional
    public Payout markPaid(long payoutId, String utr) {
        return markPaid(payoutId, utr, null);
    }

    /** Manual confirmation after the bank has paid; {@code paidOn} is the date the bank paid (defaults to now). */
    @Transactional
    public Payout markPaid(long payoutId, String utr, java.time.LocalDate paidOn) {
        Payout p = requireInFlight(payoutId);
        if (paidOn != null && paidOn.isAfter(java.time.LocalDate.now())) {
            throw new BusinessRuleException("The payment date cannot be in the future");
        }
        logTxn(p.getId(), PayoutTransaction.EventType.MANUAL_UPDATE, "SUCCESS", utr, null);
        applyFinalStatus(p, Payout.Status.SUCCESS, utr, null, null);
        if (paidOn != null) {
            p.setSettledAt(paidOn.atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant());
        }
        return p;
    }

    /**
     * Confirm several payments at once after the bank has paid them: one region/area, the producers with no
     * region, or the whole batch. {@code regionId}: null = every region, 0 = producers with no region.
     * All confirmed lines carry the same bank reference (for example the bank's batch / file reference).
     */
    @Transactional
    public List<Payout> confirmMany(long settlementId, Long regionId, String utr, java.time.LocalDate paidOn) {
        if (utr == null || utr.isBlank()) {
            throw new BusinessRuleException("Enter the bank reference for these payments");
        }
        ApprovedSettlement st = settlements.requireApprovedForPayout(settlementId);
        java.util.Set<Long> itemIds = st.items().stream()
                .filter(i -> regionId == null || (regionId == 0 ? i.regionId() == null : regionId.equals(i.regionId())))
                .map(PayableItem::settlementItemId)
                .collect(java.util.stream.Collectors.toSet());
        List<Payout> targets = payouts.findBySettlementIdOrderByProducerIdAsc(settlementId).stream()
                .filter(p -> itemIds.contains(p.getSettlementItemId()))
                .filter(p -> p.getStatus() == Payout.Status.INITIATED || p.getStatus() == Payout.Status.PROCESSING
                        || p.getStatus() == Payout.Status.PENDING)
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessRuleException("There are no payments waiting for confirmation here");
        }
        List<Payout> done = new java.util.ArrayList<>();
        for (Payout p : targets) {
            done.add(markPaid(p.getId(), utr.trim(), paidOn));
        }
        audit.record("PAYOUTS_CONFIRMED", "Settlement", settlementId,
                "%d payment(s) confirmed%s with bank reference %s".formatted(done.size(),
                        regionId == null ? "" : regionId == 0 ? " (no region)" : " for region " + regionId, utr.trim()));
        return done;
    }

    @Transactional
    public Payout markFailed(long payoutId, String reason) {
        Payout p = requireInFlight(payoutId);
        logTxn(p.getId(), PayoutTransaction.EventType.MANUAL_UPDATE, "FAILED", null, null);
        applyFinalStatus(p, Payout.Status.FAILED, null, "manual", reason);
        return p;
    }

    private void applyFinalStatus(Payout p, Payout.Status status, String utr,
                                  String failureCode, String failureReason) {
        switch (status) {
            case SUCCESS -> {
                p.setStatus(Payout.Status.SUCCESS);
                p.setUtr(utr);
                p.setSettledAt(Instant.now());
                settlements.updateItemPaymentStatus(p.getSettlementItemId(), ItemPaymentStatus.SUCCESS, utr);
                notifyPayment(p, "PAYMENT_SUCCESS", true);
            }
            case FAILED -> {
                p.setStatus(Payout.Status.FAILED);
                p.setFailureCode(failureCode);
                p.setFailureReason(failureReason);
                settlements.updateItemPaymentStatus(p.getSettlementItemId(), ItemPaymentStatus.FAILED, null);
                notifyPayment(p, "PAYMENT_FAILED", false);
            }
            default -> throw new IllegalArgumentException("Unsupported manual status " + status);
        }
        settlements.refreshSettlementPaymentStatus(p.getSettlementId());
        audit.record("PAYOUT_STATUS_CHANGED", "Payout", p.getId(), "Payment status -> " + p.getStatus());
    }

    private void notifyPayment(Payout p, String templateCode, boolean success) {
        Map<String, Object> data = new HashMap<>();
        data.put("amount", p.getAmount());
        data.put("paymentReference", success ? p.getUtr() : "");
        data.put("paymentStatus", p.getStatus().name());
        notifications.publish(new NotificationRequest(templateCode, templateCode, p.getProducerId(),
                null, "Payout", p.getId(), data));
    }

    private Payout requireInFlight(long payoutId) {
        Payout p = require(payoutId);
        if (p.getStatus() != Payout.Status.INITIATED && p.getStatus() != Payout.Status.PROCESSING
                && p.getStatus() != Payout.Status.PENDING) {
            throw new BusinessRuleException("Payout is " + p.getStatus() + "; cannot update");
        }
        return p;
    }

    private void logTxn(Long payoutId, PayoutTransaction.EventType type, String status,
                        String providerRef, Map<String, Object> payload) {
        PayoutTransaction t = new PayoutTransaction();
        t.setPayoutId(payoutId);
        t.setEventType(type);
        t.setStatus(status);
        t.setProviderRef(providerRef);
        if (type == PayoutTransaction.EventType.REQUEST || type == PayoutTransaction.EventType.RETRY) {
            t.setRequestPayload(payload);
        } else {
            t.setResponsePayload(payload);
        }
        payoutTxns.save(t);
    }

    private static Map<String, Object> requestPayload(Payout p) {
        Map<String, Object> m = new HashMap<>();
        m.put("amount", p.getAmount());
        return m;
    }

    private static ItemPaymentStatus toItemStatus(Payout.Status s) {
        return switch (s) {
            case PENDING, INITIATED -> ItemPaymentStatus.INITIATED;
            case PROCESSING -> ItemPaymentStatus.PROCESSING;
            case SUCCESS -> ItemPaymentStatus.SUCCESS;
            case FAILED -> ItemPaymentStatus.FAILED;
            case CANCELLED, REVERSED -> ItemPaymentStatus.CANCELLED;
        };
    }
}
