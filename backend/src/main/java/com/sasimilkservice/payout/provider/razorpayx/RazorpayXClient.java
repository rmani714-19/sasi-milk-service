package com.sasimilkservice.payout.provider.razorpayx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin REST wrapper over the RazorpayX Payouts API. Only the calls the payout
 * engine needs: contact + fund account create, payout create (idempotent) and
 * payout fetch. Non-2xx responses are translated to {@link RazorpayXException}
 * (which carries the retryable flag). No secrets are logged.
 */
public class RazorpayXClient {

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String razorpayxAccountNumber;

    public RazorpayXClient(RestClient.Builder builder, ObjectMapper mapper, RazorpayXProperties props) {
        this.mapper = mapper;
        this.razorpayxAccountNumber = props.accountNumber();
        String basic = Base64.getEncoder().encodeToString(
                (props.keyId() + ":" + props.keySecret()).getBytes(StandardCharsets.UTF_8));
        this.http = builder
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public record PayoutView(String id, String status, String utr, String failureReason) {
    }

    public String createContact(String name, String referenceId) {
        JsonNode res = post("/contacts", Map.of(
                "name", safe(name),
                "type", "vendor",
                "reference_id", safe(referenceId)), null);
        return res.path("id").asText();
    }

    public String createFundAccount(String contactId, String holderName, String ifsc, String accountNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contact_id", contactId);
        body.put("account_type", "bank_account");
        body.put("bank_account", Map.of(
                "name", safe(holderName),
                "ifsc", safe(ifsc),
                "account_number", safe(accountNumber)));
        return post("/fund_accounts", body, null).path("id").asText();
    }

    public PayoutView createPayout(String idempotencyKey, String fundAccountId, long amountPaise,
                                   String referenceId, String narration) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account_number", razorpayxAccountNumber);
        body.put("fund_account_id", fundAccountId);
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("mode", "IMPS");
        body.put("purpose", "payout");
        body.put("queue_if_low_balance", false);
        body.put("reference_id", safe(referenceId));
        body.put("narration", narration == null ? "SASI MILK SERVICE payment" : narration);
        JsonNode res = post("/payouts", body, idempotencyKey);
        return toPayoutView(res);
    }

    public PayoutView getPayout(String payoutId) {
        return toPayoutView(get("/payouts/" + payoutId));
    }

    /**
     * RazorpayX does not expose a stable public "available balance" endpoint on the
     * Payouts API — balance verification is done through the manual flow. Returns
     * empty rather than guessing.
     */
    public java.util.Optional<BigDecimal> fetchBalance() {
        return java.util.Optional.empty();
    }

    // ---- internals ----

    private PayoutView toPayoutView(JsonNode n) {
        String failure = n.hasNonNull("failure_reason") ? n.get("failure_reason").asText()
                : n.path("status_details").path("description").asText(null);
        return new PayoutView(n.path("id").asText(), n.path("status").asText(),
                n.hasNonNull("utr") ? n.get("utr").asText() : null, failure);
    }

    private JsonNode post(String path, Object body, String idempotencyKey) {
        try {
            String raw = http.post().uri(path)
                    .headers(h -> {
                        if (idempotencyKey != null) {
                            h.set("X-Payout-Idempotency", idempotencyKey);
                        }
                    })
                    .body(body)
                    .exchange((req, res) -> readOrThrow(res.getStatusCode().value(), res.getBody().readAllBytes()), true);
            return mapper.readTree(raw);
        } catch (RazorpayXException e) {
            throw e;
        } catch (Exception e) {
            throw new RazorpayXException("RazorpayX POST " + path + " failed", e);
        }
    }

    private JsonNode get(String path) {
        try {
            String raw = http.get().uri(path)
                    .exchange((req, res) -> readOrThrow(res.getStatusCode().value(), res.getBody().readAllBytes()), true);
            return mapper.readTree(raw);
        } catch (RazorpayXException e) {
            throw e;
        } catch (Exception e) {
            throw new RazorpayXException("RazorpayX GET " + path + " failed", e);
        }
    }

    private String readOrThrow(int status, byte[] bodyBytes) {
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        if (status >= 200 && status < 300) {
            return body;
        }
        String code = "PROVIDER_ERROR";
        String description = "HTTP " + status;
        try {
            JsonNode err = mapper.readTree(body).path("error");
            if (!err.isMissingNode()) {
                code = err.path("code").asText(code);
                description = err.path("description").asText(description);
            }
        } catch (Exception ignored) {
            // non-JSON error body — keep the defaults, never log the raw body
        }
        throw new RazorpayXException(status, code, description);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
