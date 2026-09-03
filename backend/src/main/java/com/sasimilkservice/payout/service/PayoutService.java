package com.sasimilkservice.payout.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.notification.spi.NotificationPublisher;
import com.sasimilkservice.notification.spi.NotificationPublisher.NotificationRequest;
import com.sasimilkservice.payout.domain.Payout;
import com.sasimilkservice.payout.domain.PayoutTransaction;
import com.sasimilkservice.payout.provider.PayoutProvider;
import com.sasimilkservice.payout.provider.PayoutProvider.CreateCommand;
import com.sasimilkservice.payout.provider.PayoutProvider.CreateResult;
import com.sasimilkservice.payout.provider.PayoutProvider.ProviderStatus;
import com.sasimilkservice.payout.provider.PayoutProvider.WebhookResult;
import com.sasimilkservice.payout.repository.PayoutRepository;
import com.sasimilkservice.payout.repository.PayoutTransactionRepository;
import com.sasimilkservice.producer.spi.BankAccountResolver;
import com.sasimilkservice.producer.spi.BankAccountResolver.PayoutBankAccount;
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
 * Producer payout: create one disbursement per approved settlement item, track it
 * through the provider, process webhooks, and drive settlement payment status.
 * Idempotent on {@code po-<settlementItemId>}; a create failure never leaves a
 * duplicate (unique {@code settlement_item_id}).
 */
@Service
@Transactional(readOnly = true)
public class PayoutService {

    private final PayoutRepository payouts;
    private final PayoutTransactionRepository payoutTxns;
    private final PayoutProvider provider;
    private final SettlementPayoutGateway settlements;
    private final BankAccountResolver bankAccounts;
    private final NotificationPublisher notifications;
    private final AuditRecorder audit;

    public PayoutService(PayoutRepository payouts,
                         PayoutTransactionRepository payoutTxns,
                         PayoutProvider provider,
                         SettlementPayoutGateway settlements,
                         BankAccountResolver bankAccounts,
                         NotificationPublisher notifications,
                         AuditRecorder audit) {
        this.payouts = payouts;
        this.payoutTxns = payoutTxns;
        this.provider = provider;
        this.settlements = settlements;
        this.bankAccounts = bankAccounts;
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
        ApprovedSettlement settlement = settlements.requireApprovedForPayout(settlementId);
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

    private Payout createPayout(long settlementId, String settlementNumber, PayableItem item) {
        // resolve the bank account fresh (the settlement snapshot may predate it)
        Long bankAccountId = item.bankAccountId() != null
                ? item.bankAccountId()
                : bankAccounts.primaryActiveAccountId(item.producerId()).orElse(null);
        Optional<PayoutBankAccount> bank = bankAccountId == null
                ? Optional.empty()
                : bankAccounts.payoutDetails(bankAccountId);
        if (bank.isEmpty()) {
            // cannot pay a producer with no bank account — hold the line for an operator
            settlements.updateItemPaymentStatus(item.settlementItemId(), ItemPaymentStatus.ON_HOLD, null);
            audit.record("PAYOUT_CREATED", "SettlementItem", item.settlementItemId(),
                    "Payout on hold for producer %d: no active bank account".formatted(item.producerId()));
            return null;
        }

        Payout p = new Payout();
        p.setSettlementId(settlementId);
        p.setSettlementItemId(item.settlementItemId());
        p.setProducerId(item.producerId());
        p.setAmount(MoneyMath.money(item.netPayable()));
        p.setIdempotencyKey("po-" + item.settlementItemId());
        p.setProvider(provider.name());

        PayoutBankAccount b = bank.get();
        p.setBankAccountId(b.bankAccountId());
        p.setStatus(Payout.Status.PENDING);
        p = payouts.save(p);
        logTxn(p.getId(), PayoutTransaction.EventType.REQUEST, "PENDING", null, requestPayload(p, b));

        CreateResult r = provider.create(new CreateCommand(
                p.getIdempotencyKey(), p.getAmount(), p.getCurrencyCode(),
                b.accountHolderName(), b.accountNumber(), b.ifsc(), b.bankName(),
                settlementNumber + " / producer " + item.producerId()));

        p.setProviderPayoutId(r.providerPayoutId());
        p.setProviderRef(r.providerRef());
        p.setStatus(mapStatus(r.status()));
        p.setAttemptCount(1);
        p.setLastAttemptAt(Instant.now());
        logTxn(p.getId(), PayoutTransaction.EventType.RESPONSE, r.status().name(), r.providerRef(),
                Map.of("providerPayoutId", String.valueOf(r.providerPayoutId()),
                        "raw", String.valueOf(r.rawResponse())));

        settlements.updateItemPaymentStatus(item.settlementItemId(),
                toItemStatus(p.getStatus()), null);
        audit.record("PAYOUT_CREATED", "Payout", p.getId(),
                "Payout %s to producer %d, status %s".formatted(p.getAmount(), item.producerId(), p.getStatus()));
        return p;
    }

    @Transactional
    public Payout retry(long payoutId) {
        Payout p = require(payoutId);
        if (p.getStatus() != Payout.Status.FAILED) {
            throw new BusinessRuleException("Only a FAILED payout can be retried");
        }
        Optional<PayoutBankAccount> bank = bankAccounts.payoutDetails(p.getBankAccountId());
        if (bank.isEmpty()) {
            throw new BusinessRuleException("Producer still has no active bank account");
        }
        PayoutBankAccount b = bank.get();

        p.setAttemptCount(p.getAttemptCount() + 1);
        p.setLastAttemptAt(Instant.now());
        p.setFailureCode(null);
        p.setFailureReason(null);
        logTxn(p.getId(), PayoutTransaction.EventType.RETRY, null, null, requestPayload(p, b));

        CreateResult r = provider.create(new CreateCommand(
                p.getIdempotencyKey(), p.getAmount(), p.getCurrencyCode(),
                b.accountHolderName(), b.accountNumber(), b.ifsc(), b.bankName(),
                "retry / payout " + p.getId()));
        p.setProviderPayoutId(r.providerPayoutId());
        p.setProviderRef(r.providerRef());
        p.setStatus(mapStatus(r.status()));
        logTxn(p.getId(), PayoutTransaction.EventType.RESPONSE, r.status().name(), r.providerRef(), null);

        settlements.updateItemPaymentStatus(p.getSettlementItemId(), toItemStatus(p.getStatus()), null);
        settlements.refreshSettlementPaymentStatus(p.getSettlementId());
        audit.record("PAYOUT_RETRIED", "Payout", p.getId(), "Payout retried, status " + p.getStatus());
        return p;
    }

    @Transactional
    public void handleWebhook(String rawBody, Map<String, String> headers) {
        WebhookResult w = provider.parseWebhook(rawBody, headers);
        if (!w.signatureValid()) {
            throw new BusinessRuleException("invalid_signature", "Webhook signature verification failed");
        }
        Payout p = payouts.findByProviderPayoutId(w.providerPayoutId())
                .orElseThrow(() -> new NotFoundException("Unknown payout " + w.providerPayoutId()));

        if (w.providerRef() != null && payoutTxns.existsByPayoutIdAndEventTypeAndProviderRef(
                p.getId(), PayoutTransaction.EventType.WEBHOOK, w.providerRef())) {
            return; // already processed this webhook
        }
        logTxn(p.getId(), PayoutTransaction.EventType.WEBHOOK, w.status().name(), w.providerRef(), null);
        applyFinalStatus(p, w.status(), w.utr(), w.failureCode(), w.failureReason());
    }

    @Transactional
    public Payout markPaid(long payoutId, String utr) {
        Payout p = requireInFlight(payoutId);
        logTxn(p.getId(), PayoutTransaction.EventType.MANUAL_UPDATE, "SUCCESS", utr, null);
        applyFinalStatus(p, ProviderStatus.SUCCESS, utr, null, null);
        return p;
    }

    @Transactional
    public Payout markFailed(long payoutId, String reason) {
        Payout p = requireInFlight(payoutId);
        logTxn(p.getId(), PayoutTransaction.EventType.MANUAL_UPDATE, "FAILED", null, null);
        applyFinalStatus(p, ProviderStatus.FAILED, null, "manual", reason);
        return p;
    }

    private void applyFinalStatus(Payout p, ProviderStatus status, String utr,
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
            default -> {
                p.setStatus(mapStatus(status));
                settlements.updateItemPaymentStatus(p.getSettlementItemId(), toItemStatus(p.getStatus()), null);
            }
        }
        settlements.refreshSettlementPaymentStatus(p.getSettlementId());
        audit.record("PAYOUT_STATUS_CHANGED", "Payout", p.getId(), "Payout status -> " + p.getStatus());
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

    private static Map<String, Object> requestPayload(Payout p, PayoutBankAccount b) {
        Map<String, Object> m = new HashMap<>();
        m.put("amount", p.getAmount());
        m.put("ifsc", b.ifsc());
        m.put("accountLast4", b.accountNumber() == null ? null
                : b.accountNumber().substring(Math.max(0, b.accountNumber().length() - 4)));
        return m;
    }

    private static Payout.Status mapStatus(ProviderStatus s) {
        return switch (s) {
            case PENDING -> Payout.Status.INITIATED;
            case PROCESSING -> Payout.Status.PROCESSING;
            case SUCCESS -> Payout.Status.SUCCESS;
            case FAILED -> Payout.Status.FAILED;
        };
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
