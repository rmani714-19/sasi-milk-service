package com.sasimilkservice.payout.provider.razorpayx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sasimilkservice.common.crypto.CryptoProperties;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.payout.provider.PayoutProvider.CreateCommand;
import com.sasimilkservice.payout.provider.PayoutProvider.CreateResult;
import com.sasimilkservice.payout.provider.PayoutProvider.ProviderStatus;
import com.sasimilkservice.payout.provider.PayoutProvider.WebhookResult;
import com.sasimilkservice.payout.provider.razorpayx.RazorpayXClient.PayoutView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RazorpayXPayoutProviderTest {

    private static final String WEBHOOK_SECRET = "whsec_test_123";

    private RazorpayXClient client;
    private PaymentProviderAccountRepository accounts;
    private RazorpayXPayoutProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(RazorpayXClient.class);
        accounts = mock(PaymentProviderAccountRepository.class);
        FieldCrypto crypto = new FieldCrypto(new CryptoProperties("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        RazorpayXProperties props = new RazorpayXProperties(true, "rzp_test_x", "secret",
                "9999999999", WEBHOOK_SECRET, null);
        provider = new RazorpayXPayoutProvider(client, accounts, crypto, new ObjectMapper(), props);
    }

    private CreateCommand cmd(String key) {
        return new CreateCommand(key, new BigDecimal("1234.50"), "INR",
                "Ramesh Kumar", "50100123456789", "HDFC0001234", "HDFC Bank", "WS-1 / producer 7");
    }

    @Test
    void create_maps_provider_status_and_carries_the_idempotency_key() {
        when(accounts.findByProviderAndAccountNumberHashAndIfsc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(client.createContact(any(), any())).thenReturn("cont_1");
        when(client.createFundAccount(any(), any(), any(), any())).thenReturn("fa_1");
        when(client.createPayout(eq("po-7"), eq("fa_1"), eq(123450L), any(), any()))
                .thenReturn(new PayoutView("pout_ABC", "processing", null, null));

        CreateResult r = provider.create(cmd("po-7"));

        assertThat(r.providerPayoutId()).isEqualTo("pout_ABC");
        assertThat(r.providerRef()).isEqualTo("po-7");
        assertThat(r.status()).isEqualTo(ProviderStatus.PROCESSING);
    }

    @Test
    void create_reuses_a_cached_fund_account_and_does_not_recreate_contact() {
        PaymentProviderAccount cached = new PaymentProviderAccount();
        cached.setFundAccountId("fa_cached");
        when(accounts.findByProviderAndAccountNumberHashAndIfsc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(cached));
        when(client.createPayout(any(), eq("fa_cached"), anyLong(), any(), any()))
                .thenReturn(new PayoutView("pout_2", "queued", null, null));

        provider.create(cmd("po-9"));

        verify(client, never()).createContact(any(), any());
        verify(client, never()).createFundAccount(any(), any(), any(), any());
    }

    @Test
    void create_returns_FAILED_for_a_non_retryable_provider_error_instead_of_throwing() {
        when(accounts.findByProviderAndAccountNumberHashAndIfsc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(fa("fa_1")));
        when(client.createPayout(any(), any(), anyLong(), any(), any()))
                .thenThrow(new RazorpayXException(400, "BAD_REQUEST_ERROR", "The ifsc provided is invalid"));

        CreateResult r = provider.create(cmd("po-10"));

        assertThat(r.status()).isEqualTo(ProviderStatus.FAILED);
        assertThat(r.providerPayoutId()).isNull();
        assertThat(r.rawResponse()).contains("INVALID_IFSC");
    }

    @Test
    void create_rethrows_a_retryable_provider_error_so_the_deterministic_key_retry_is_safe() {
        when(accounts.findByProviderAndAccountNumberHashAndIfsc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(fa("fa_1")));
        when(client.createPayout(any(), any(), anyLong(), any(), any()))
                .thenThrow(new RazorpayXException(503, "SERVER_ERROR", "temporarily down"));

        org.junit.jupiter.api.Assertions.assertThrows(RazorpayXException.class, () -> provider.create(cmd("po-11")));
    }

    // ---- webhook ----

    private String signed(String body) {
        return RazorpayXPayoutProvider.hmacSha256Hex(body, WEBHOOK_SECRET);
    }

    private static final String PROCESSED_BODY = """
            {"event":"payout.processed","payload":{"payout":{"entity":{"id":"pout_ABC","status":"processed","utr":"HDFCN123456"}}}}""";

    @Test
    void valid_signature_yields_a_parsed_successful_webhook() {
        WebhookResult w = provider.parseWebhook(PROCESSED_BODY, Map.of(
                "x-razorpay-signature", signed(PROCESSED_BODY),
                "x-razorpay-event-id", "evt_1"));

        assertThat(w.signatureValid()).isTrue();
        assertThat(w.eventId()).isEqualTo("evt_1");
        assertThat(w.providerPayoutId()).isEqualTo("pout_ABC");
        assertThat(w.status()).isEqualTo(ProviderStatus.SUCCESS);
        assertThat(w.utr()).isEqualTo("HDFCN123456");
        assertThat(w.eventType()).isEqualTo("payout.processed");
    }

    @Test
    void tampered_body_fails_signature_verification() {
        // signature computed over the original body, but a different body is delivered
        WebhookResult w = provider.parseWebhook(PROCESSED_BODY.replace("pout_ABC", "pout_EVIL"), Map.of(
                "x-razorpay-signature", signed(PROCESSED_BODY),
                "x-razorpay-event-id", "evt_2"));
        assertThat(w.signatureValid()).isFalse();
        assertThat(w.status()).isNull();
    }

    @Test
    void missing_signature_header_is_rejected() {
        WebhookResult w = provider.parseWebhook(PROCESSED_BODY, Map.of("x-razorpay-event-id", "evt_3"));
        assertThat(w.signatureValid()).isFalse();
    }

    @Test
    void failed_payout_webhook_maps_to_FAILED_with_reason() {
        String body = """
                {"event":"payout.failed","payload":{"payout":{"entity":{"id":"pout_X","status":"failed","failure_reason":"beneficiary bank offline"}}}}""";
        WebhookResult w = provider.parseWebhook(body, Map.of(
                "x-razorpay-signature", signed(body), "x-razorpay-event-id", "evt_4"));
        assertThat(w.status()).isEqualTo(ProviderStatus.FAILED);
        assertThat(w.failureReason()).isEqualTo("beneficiary bank offline");
    }

    @Test
    void reversed_payout_webhook_maps_to_REVERSED() {
        String body = """
                {"event":"payout.reversed","payload":{"payout":{"entity":{"id":"pout_X","status":"reversed"}}}}""";
        WebhookResult w = provider.parseWebhook(body, Map.of(
                "x-razorpay-signature", signed(body), "x-razorpay-event-id", "evt_5"));
        assertThat(w.status()).isEqualTo(ProviderStatus.REVERSED);
    }

    private static PaymentProviderAccount fa(String id) {
        PaymentProviderAccount a = new PaymentProviderAccount();
        a.setFundAccountId(id);
        return a;
    }
}
