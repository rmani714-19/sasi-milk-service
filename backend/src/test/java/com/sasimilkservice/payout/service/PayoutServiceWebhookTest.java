package com.sasimilkservice.payout.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.notification.spi.NotificationPublisher;
import com.sasimilkservice.payout.domain.PaymentWebhookEvent;
import com.sasimilkservice.payout.domain.Payout;
import com.sasimilkservice.payout.provider.PayoutProvider;
import com.sasimilkservice.payout.provider.PayoutProvider.ProviderStatus;
import com.sasimilkservice.payout.provider.PayoutProvider.WebhookResult;
import com.sasimilkservice.payout.repository.PaymentWebhookEventRepository;
import com.sasimilkservice.payout.repository.PayoutRepository;
import com.sasimilkservice.payout.repository.PayoutTransactionRepository;
import com.sasimilkservice.producer.spi.BankAccountResolver;
import com.sasimilkservice.settlement.spi.SettlementPayoutGateway;
import com.sasimilkservice.settlement.spi.SettlementPayoutGateway.ItemPaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutServiceWebhookTest {

    private PayoutRepository payouts;
    private PaymentWebhookEventRepository webhookEvents;
    private PayoutProvider provider;
    private SettlementPayoutGateway settlements;
    private NotificationPublisher notifications;
    private PayoutService service;

    @BeforeEach
    void setUp() {
        payouts = mock(PayoutRepository.class);
        PayoutTransactionRepository payoutTxns = mock(PayoutTransactionRepository.class);
        webhookEvents = mock(PaymentWebhookEventRepository.class);
        provider = mock(PayoutProvider.class);
        settlements = mock(SettlementPayoutGateway.class);
        BankAccountResolver bankAccounts = mock(BankAccountResolver.class);
        notifications = mock(NotificationPublisher.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        service = new PayoutService(payouts, payoutTxns, webhookEvents, provider, settlements,
                bankAccounts, notifications, audit);

        when(provider.name()).thenReturn("RAZORPAYX");
        Payout p = new Payout();
        p.setSettlementId(1L);
        p.setSettlementItemId(10L);
        p.setProducerId(7L);
        p.setAmount(new BigDecimal("100.00"));
        p.setStatus(Payout.Status.PROCESSING);
        p.setProviderPayoutId("pout_1");
        when(payouts.findByProviderPayoutId("pout_1")).thenReturn(Optional.of(p));
    }

    private WebhookResult success(String eventId) {
        return new WebhookResult(eventId, true, "pout_1", "pout_1", ProviderStatus.SUCCESS,
                "UTR123", null, null, "payout.processed");
    }

    @Test
    void a_duplicate_webhook_is_applied_and_notified_only_once() {
        when(provider.parseWebhook(any(), any())).thenReturn(success("evt_dup"));
        // first delivery: not seen yet
        when(webhookEvents.findByProviderAndEventId("RAZORPAYX", "evt_dup"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new PaymentWebhookEvent())); // redelivery: already recorded

        service.handleWebhook("{}", Map.of());
        service.handleWebhook("{}", Map.of()); // redelivery

        verify(settlements, times(1)).updateItemPaymentStatus(eq(10L), eq(ItemPaymentStatus.SUCCESS), eq("UTR123"));
        verify(notifications, times(1)).publish(any());
        verify(webhookEvents, times(1)).save(any());
    }

    @Test
    void an_invalid_signature_never_records_an_event_or_touches_a_payout() {
        when(provider.parseWebhook(any(), any())).thenReturn(new WebhookResult(
                "evt_bad", false, null, null, null, null, "invalid_signature", "bad sig", null));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.handleWebhook("{}", Map.of()));

        verify(webhookEvents, times(0)).save(any());
        verify(settlements, times(0)).updateItemPaymentStatus(anyLong(), any(), any());
    }

    @Test
    void a_reversed_webhook_cancels_the_settlement_line() {
        when(provider.parseWebhook(any(), any())).thenReturn(new WebhookResult(
                "evt_rev", true, "pout_1", "pout_1", ProviderStatus.REVERSED, null,
                "REVERSED", "chargeback", "payout.reversed"));
        when(webhookEvents.findByProviderAndEventId("RAZORPAYX", "evt_rev")).thenReturn(Optional.empty());

        service.handleWebhook("{}", Map.of());

        verify(settlements, times(1)).updateItemPaymentStatus(eq(10L), eq(ItemPaymentStatus.CANCELLED), any());
        verify(notifications, times(1)).publish(any());
    }
}
