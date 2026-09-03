package com.sasimilkservice.payout.provider.razorpayx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.payout.provider.PayoutProvider;
import com.sasimilkservice.payout.provider.razorpayx.RazorpayXClient.PayoutView;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * RazorpayX implementation of {@link PayoutProvider}. Registered only when
 * {@code sasi.payout.razorpayx.enabled=true}; otherwise the MANUAL provider is used.
 *
 * <ul>
 *   <li>Contacts + fund accounts are created once per bank account and reused
 *       ({@code payment_provider_accounts}).</li>
 *   <li>Payout creation carries the deterministic idempotency key
 *       ({@code po-<settlementItemId>}) as {@code X-Payout-Idempotency}, so an
 *       uncertain retry never double-pays.</li>
 *   <li>A retryable transport / 5xx error is propagated (the deterministic key
 *       makes the retry safe); a non-retryable error (bad account, invalid IFSC)
 *       is returned as a FAILED result for operator attention.</li>
 *   <li>Webhooks are HMAC-SHA256 signature verified before anything is applied.</li>
 * </ul>
 */
public class RazorpayXPayoutProvider implements PayoutProvider {

    static final String NAME = "RAZORPAYX";

    private final RazorpayXClient client;
    private final PaymentProviderAccountRepository providerAccounts;
    private final FieldCrypto crypto;
    private final ObjectMapper mapper;
    private final String webhookSecret;

    public RazorpayXPayoutProvider(RazorpayXClient client,
                                   PaymentProviderAccountRepository providerAccounts,
                                   FieldCrypto crypto,
                                   ObjectMapper mapper,
                                   RazorpayXProperties props) {
        this.client = client;
        this.providerAccounts = providerAccounts;
        this.crypto = crypto;
        this.mapper = mapper;
        this.webhookSecret = props.webhookSecret();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    @Transactional
    public CreateResult create(CreateCommand cmd) {
        String fundAccountId = resolveFundAccount(cmd);
        long amountPaise = cmd.amount().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        try {
            PayoutView pv = client.createPayout(cmd.idempotencyKey(), fundAccountId, amountPaise,
                    cmd.reference(), "SASI MILK SERVICE producer or driver payment");
            return new CreateResult(pv.id(), cmd.idempotencyKey(),
                    RazorpayXStatusMapper.map(pv.status()),
                    "razorpayx payout " + pv.id() + " status=" + pv.status());
        } catch (RazorpayXException e) {
            if (e.retryable()) {
                throw e; // safe to retry — same deterministic idempotency key
            }
            RazorpayXErrorMapper.MappedError m = RazorpayXErrorMapper.map(e.httpStatus(), e.code(), e.getMessage());
            return new CreateResult(null, cmd.idempotencyKey(), ProviderStatus.FAILED,
                    m.failureCode() + ": " + m.failureReason());
        }
    }

    private String resolveFundAccount(CreateCommand cmd) {
        String hash = crypto.blindIndex(cmd.accountNumber() == null ? "" : cmd.accountNumber().replaceAll("\\s", ""));
        String ifsc = cmd.ifsc() == null ? "" : cmd.ifsc().trim().toUpperCase();
        return providerAccounts.findByProviderAndAccountNumberHashAndIfsc(NAME, hash, ifsc)
                .map(PaymentProviderAccount::getFundAccountId)
                .orElseGet(() -> {
                    String contactId = client.createContact(cmd.accountHolderName(), cmd.reference());
                    String faId = client.createFundAccount(contactId, cmd.accountHolderName(), ifsc, cmd.accountNumber());
                    PaymentProviderAccount row = new PaymentProviderAccount();
                    row.setProvider(NAME);
                    row.setAccountNumberHash(hash);
                    row.setIfsc(ifsc);
                    row.setContactId(contactId);
                    row.setFundAccountId(faId);
                    providerAccounts.save(row);
                    return faId;
                });
    }

    @Override
    public ProviderStatus fetchStatus(String providerPayoutId) {
        return RazorpayXStatusMapper.map(client.getPayout(providerPayoutId).status());
    }

    @Override
    public Optional<ProviderBalance> getBalance() {
        return client.fetchBalance().map(b -> new ProviderBalance(b, "INR"));
    }

    @Override
    public WebhookResult parseWebhook(String rawBody, Map<String, String> headers) {
        String eventId = header(headers, "x-razorpay-event-id");
        String provided = header(headers, "x-razorpay-signature");
        boolean valid = provided != null && constantTimeEquals(provided, hmacSha256Hex(rawBody, webhookSecret));
        if (!valid) {
            return new WebhookResult(eventId, false, null, null, null, null,
                    "invalid_signature", "webhook signature verification failed", null);
        }
        try {
            JsonNode root = mapper.readTree(rawBody == null ? "{}" : rawBody);
            String eventType = root.path("event").asText(null);
            JsonNode entity = root.path("payload").path("payout").path("entity");
            String payoutId = entity.path("id").asText(null);
            String status = entity.path("status").asText(null);
            String utr = entity.hasNonNull("utr") ? entity.get("utr").asText() : null;
            String failureReason = entity.hasNonNull("failure_reason") ? entity.get("failure_reason").asText() : null;
            ProviderStatus mapped = refine(eventType, RazorpayXStatusMapper.map(status));
            String failureCode = mapped == ProviderStatus.FAILED
                    ? (failureReason == null ? "PAYOUT_FAILED" : "PAYOUT_FAILED")
                    : null;
            return new WebhookResult(eventId, true, payoutId, payoutId, mapped, utr,
                    failureCode, failureReason, eventType);
        } catch (Exception e) {
            return new WebhookResult(eventId, true, null, null, ProviderStatus.PROCESSING, null,
                    "parse_error", "could not parse webhook body", null);
        }
    }

    private static ProviderStatus refine(String eventType, ProviderStatus fromEntity) {
        if (eventType == null) {
            return fromEntity;
        }
        return switch (eventType) {
            case "payout.processed" -> ProviderStatus.SUCCESS;
            case "payout.failed", "payout.rejected" -> ProviderStatus.FAILED;
            case "payout.reversed" -> ProviderStatus.REVERSED;
            default -> fromEntity;
        };
    }

    // ---- signature ----

    static String hmacSha256Hex(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmac failed", e);
        }
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        String v = headers.get(name);
        return v != null ? v : headers.get(name.toLowerCase());
    }
}
