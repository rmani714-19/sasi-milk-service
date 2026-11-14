package com.sasimilkservice.paymentops.api;

import com.sasimilkservice.paymentops.service.PreValidation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shared payloads for the pre-validation and approval steps (settlements + salary batches). */
public final class PaymentOpsDtos {

    private PaymentOpsDtos() {}

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
