package com.sasimilkservice.paymentops.api;

import com.sasimilkservice.paymentops.domain.BankBalanceCheck;
import com.sasimilkservice.paymentops.service.PreValidation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shared payloads for the bank-balance / pre-validation steps (settlements + salary batches). */
public final class PaymentOpsDtos {

    private PaymentOpsDtos() {}

    public record BankBalanceRequest(
            Long centerBankAccountId,
            String bankAccountLabel,
            @NotNull @DecimalMin("0.0") BigDecimal availableBalance,
            BankBalanceCheck.Method method,
            String note) {
    }

    public record BankBalanceCheckResponse(
            Long id,
            Long centerBankAccountId,
            String bankAccountLabel,
            BigDecimal availableBalance,
            BigDecimal requiredAmount,
            BigDecimal remainingAfter,
            boolean sufficient,
            String method,
            String note,
            Long verifiedBy,
            Instant verifiedAt) {

        public static BankBalanceCheckResponse from(BankBalanceCheck c) {
            return new BankBalanceCheckResponse(c.getId(), c.getCenterBankAccountId(), c.getBankAccountLabel(),
                    c.getAvailableBalance(), c.getRequiredAmount(), c.getRemainingAfter(), c.isSufficient(),
                    c.getMethod().name(), c.getNote(), c.getVerifiedBy(), c.getVerifiedAt());
        }
    }

    public record PreValidationResponse(String result, List<CheckResponse> checks) {

        public static PreValidationResponse from(PreValidation.Report r) {
            return new PreValidationResponse(r.result().name(),
                    r.checks().stream().map(CheckResponse::from).toList());
        }
    }

    public record CheckResponse(String key, String label, boolean passed, boolean blocking,
                                String detail, String link) {

        static CheckResponse from(PreValidation.Check c) {
            return new CheckResponse(c.key(), c.label(), c.passed(), c.blocking(), c.detail(), c.link());
        }
    }

    public record ApproveRequest(Boolean confirmed) {
    }
}
