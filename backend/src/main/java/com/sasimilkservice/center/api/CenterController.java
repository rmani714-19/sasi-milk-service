package com.sasimilkservice.center.api;

import com.sasimilkservice.center.domain.Center;
import com.sasimilkservice.center.service.CenterService;
import com.sasimilkservice.center.service.CenterService.PayoutAccountInput;
import com.sasimilkservice.center.service.CenterService.UpdateCenterCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/center")
public class CenterController {

    private final CenterService service;

    public CenterController(CenterService service) {
        this.service = service;
    }

    @GetMapping
    public CenterResponse get() {
        return CenterResponse.from(service.get());
    }

    /** Full, decrypted payout account — restricted to whoever can run bank-balance verification. */
    @GetMapping("/payout-account")
    @PreAuthorize("hasAnyAuthority('VERIFY_PAYMENT','GENERATE_SETTLEMENT','APPROVE_SETTLEMENT','PROCESS_PAYMENT','ALL')")
    public PayoutAccountResponse payoutAccount() {
        var v = service.payoutAccountView();
        if (v == null) {
            return null;
        }
        return new PayoutAccountResponse(v.accountHolderName(), v.accountNumber(), v.ifsc(), v.bankName(), v.updatedAt());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('MANAGE_RATES')")
    public CenterResponse update(@Valid @RequestBody UpdateCenterRequest request) {
        return CenterResponse.from(service.update(request.toCommand()));
    }

    public record UpdateCenterRequest(
            @NotBlank String centerName,
            String ownerName,
            String mobileNumber,
            @Email String email,
            String address,
            @Min(1) @Max(7) Integer settlementDayOfWeek,
            String roundingMode,
            @Min(0) @Max(4) Integer roundingScale,
            String incentiveStacking,
            @DecimalMin("0.0") BigDecimal incentiveMaxPerLitre,
            Boolean allowMissingRate,
            String payoutAccountHolderName,
            String payoutAccountNumber,
            String payoutIfsc,
            String payoutBankName) {

        UpdateCenterCommand toCommand() {
            PayoutAccountInput payout = (payoutAccountHolderName == null && payoutAccountNumber == null
                    && payoutIfsc == null && payoutBankName == null)
                    ? null
                    : new PayoutAccountInput(payoutAccountHolderName, payoutAccountNumber, payoutIfsc, payoutBankName);
            return new UpdateCenterCommand(centerName, ownerName, mobileNumber, email, address,
                    settlementDayOfWeek, roundingMode, roundingScale, incentiveStacking,
                    incentiveMaxPerLitre, allowMissingRate, payout);
        }
    }

    public record CenterResponse(
            String centerName,
            String ownerName,
            String mobileNumber,
            String email,
            String address,
            String currencyCode,
            int settlementDayOfWeek,
            String roundingMode,
            int roundingScale,
            String incentiveStacking,
            BigDecimal incentiveMaxPerLitre,
            boolean allowMissingRate,
            BigDecimal fatMin,
            BigDecimal fatMax,
            BigDecimal snfMin,
            BigDecimal snfMax,
            String payoutAccountHolderName,
            String payoutAccountNumberMasked,
            String payoutIfsc,
            String payoutBankName,
            boolean payoutAccountComplete,
            Instant payoutAccountUpdatedAt) {

        static CenterResponse from(Center c) {
            return new CenterResponse(c.getCenterName(), c.getOwnerName(), c.getMobileNumber(),
                    c.getEmail(), c.getAddress(), c.getCurrencyCode(), c.getSettlementDayOfWeek(),
                    c.getRoundingMode(), c.getRoundingScale(), c.getIncentiveStacking(),
                    c.getIncentiveMaxPerLitre(), c.isAllowMissingRate(),
                    c.getFatMin(), c.getFatMax(), c.getSnfMin(), c.getSnfMax(),
                    c.getPayoutAccountHolderName(), c.maskedPayoutAccount(), c.getPayoutIfsc(),
                    c.getPayoutBankName(), c.isPayoutAccountComplete(), c.getPayoutAccountUpdatedAt());
        }
    }

    public record PayoutAccountResponse(
            String accountHolderName, String accountNumber, String ifsc, String bankName, Instant updatedAt) {
    }
}
