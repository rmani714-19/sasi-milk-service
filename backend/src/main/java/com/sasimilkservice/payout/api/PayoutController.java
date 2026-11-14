package com.sasimilkservice.payout.api;

import com.sasimilkservice.payout.domain.Payout;
import com.sasimilkservice.payout.service.PayoutService;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PayoutController {

    private final PayoutService service;

    public PayoutController(PayoutService service) {
        this.service = service;
    }

    @PostMapping("/settlements/{settlementId}/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('APPROVE_SETTLEMENT','SUBMIT_TO_BANK')")
    public List<PayoutResponse> initiate(@PathVariable long settlementId,
                                         @RequestBody(required = false) SubmitToBankRequest r) {
        var rows = r == null
                ? service.initiate(settlementId)
                : service.initiate(settlementId, r.submittedOn(), r.bankReference(), r.note());
        return rows.stream().map(PayoutResponse::from).toList();
    }

    /** Confirm many payments at once: a region, the producers with no region (regionId 0), or everything. */
    @PostMapping("/settlements/{settlementId}/payouts/confirm")
    @PreAuthorize("hasAnyAuthority('APPROVE_SETTLEMENT','CONFIRM_PAYMENT')")
    public List<PayoutResponse> confirmMany(@PathVariable long settlementId, @Valid @RequestBody ConfirmManyRequest r) {
        return service.confirmMany(settlementId, r.regionId(), r.bankReference(), r.paidOn())
                .stream().map(PayoutResponse::from).toList();
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
    @PreAuthorize("hasAnyAuthority('APPROVE_SETTLEMENT','CONFIRM_PAYMENT')")
    public PayoutResponse retry(@PathVariable long id) {
        return PayoutResponse.from(service.retry(id));
    }

    @PostMapping("/payouts/{id}/mark-paid")
    @PreAuthorize("hasAnyAuthority('APPROVE_SETTLEMENT','CONFIRM_PAYMENT')")
    public PayoutResponse markPaid(@PathVariable long id, @Valid @RequestBody MarkPaidRequest r) {
        return PayoutResponse.from(service.markPaid(id, r.utr(), r.paidOn()));
    }

    @PostMapping("/payouts/{id}/mark-failed")
    @PreAuthorize("hasAnyAuthority('APPROVE_SETTLEMENT','CONFIRM_PAYMENT')")
    public PayoutResponse markFailed(@PathVariable long id, @Valid @RequestBody MarkFailedRequest r) {
        return PayoutResponse.from(service.markFailed(id, r.reason()));
    }

    public record ConfirmManyRequest(Long regionId, @NotBlank String bankReference, java.time.LocalDate paidOn) {
    }

    public record MarkPaidRequest(@NotBlank String utr, java.time.LocalDate paidOn) {
    }

    /** Paperwork for handing a batch to the bank — no transfer is made by the application. */
    public record SubmitToBankRequest(java.time.LocalDate submittedOn, String bankReference, String note) {
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
