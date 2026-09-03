package com.sasimilkservice.payout.api;

import com.sasimilkservice.payout.domain.Payout;
import com.sasimilkservice.payout.service.PayoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PayoutController {

    private final PayoutService service;

    public PayoutController(PayoutService service) {
        this.service = service;
    }

    @PostMapping("/settlements/{settlementId}/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public List<PayoutResponse> initiate(@PathVariable long settlementId) {
        return service.initiate(settlementId).stream().map(PayoutResponse::from).toList();
    }

    @GetMapping("/payouts")
    @PreAuthorize("hasAnyAuthority('VIEW_PAYMENTS','APPROVE_SETTLEMENT')")
    public List<PayoutResponse> list(@RequestParam(required = false) Long settlementId) {
        var rows = settlementId == null ? service.all() : service.forSettlement(settlementId);
        return rows.stream().map(PayoutResponse::from).toList();
    }

    @GetMapping("/payouts/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_PAYMENTS','APPROVE_SETTLEMENT')")
    public PayoutResponse get(@PathVariable long id) {
        return PayoutResponse.from(service.require(id));
    }

    @PostMapping("/payouts/{id}/retry")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public PayoutResponse retry(@PathVariable long id) {
        return PayoutResponse.from(service.retry(id));
    }

    @PostMapping("/payouts/{id}/mark-paid")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public PayoutResponse markPaid(@PathVariable long id, @Valid @RequestBody MarkPaidRequest r) {
        return PayoutResponse.from(service.markPaid(id, r.utr()));
    }

    @PostMapping("/payouts/{id}/mark-failed")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public PayoutResponse markFailed(@PathVariable long id, @Valid @RequestBody MarkFailedRequest r) {
        return PayoutResponse.from(service.markFailed(id, r.reason()));
    }

    /** Provider webhook — authenticated by signature inside the provider adapter, not by JWT. */
    @PostMapping("/payouts/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestBody(required = false) String body, HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            headers.put(n.toLowerCase(), request.getHeader(n));
        }
        service.handleWebhook(body == null ? "" : body, headers);
    }

    public record MarkPaidRequest(@NotBlank String utr) {
    }

    public record MarkFailedRequest(@NotBlank String reason) {
    }

    public record PayoutResponse(
            Long id,
            String publicId,
            Long settlementId,
            Long settlementItemId,
            Long producerId,
            BigDecimal amount,
            String provider,
            String providerPayoutId,
            String status,
            String utr,
            String failureReason,
            int attemptCount,
            Instant settledAt) {

        static PayoutResponse from(Payout p) {
            return new PayoutResponse(p.getId(), p.getPublicId().toString(), p.getSettlementId(),
                    p.getSettlementItemId(), p.getProducerId(), p.getAmount(), p.getProvider(),
                    p.getProviderPayoutId(), p.getStatus().name(), p.getUtr(), p.getFailureReason(),
                    p.getAttemptCount(), p.getSettledAt());
        }
    }
}
