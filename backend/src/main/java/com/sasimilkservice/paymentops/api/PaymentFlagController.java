package com.sasimilkservice.paymentops.api;

import com.sasimilkservice.paymentops.domain.PaymentFlag;
import com.sasimilkservice.paymentops.service.PaymentFlagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

import java.time.Instant;
import java.util.List;

/**
 * Flags on individual payment-activity rows (payouts, driver-salary lines,
 * expenses) — deliberately a standalone resource rather than nested under
 * settlement/salary/expense controllers, since the unified Payment Activity
 * view needs one flat list of open flags across all three subject types.
 */
@RestController
@RequestMapping("/api/v1/payment-flags")
public class PaymentFlagController {

    private static final String READ = "hasAnyAuthority('VIEW_PAYMENTS','VERIFY_PAYMENT','PROCESS_PAYMENT','APPROVE_SETTLEMENT','ALL')";
    private static final String WRITE = "hasAnyAuthority('VERIFY_PAYMENT','PROCESS_PAYMENT','APPROVE_SETTLEMENT','GENERATE_SETTLEMENT','ALL')";

    private final PaymentFlagService service;

    public PaymentFlagController(PaymentFlagService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<FlagResponse> openFlags() {
        return service.openFlags().stream().map(FlagResponse::from).toList();
    }

    @GetMapping("/history")
    @PreAuthorize(READ)
    public List<FlagResponse> history(
            @RequestParam PaymentFlag.SubjectType subjectType, @RequestParam long subjectId) {
        return service.historyFor(subjectType, subjectId).stream().map(FlagResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public FlagResponse raise(@Valid @RequestBody RaiseRequest r) {
        return FlagResponse.from(service.raise(r.subjectType(), r.subjectId(), r.note()));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(WRITE)
    public FlagResponse resolve(@PathVariable long id, @RequestBody(required = false) ResolveRequest r) {
        return FlagResponse.from(service.resolve(id, r == null ? null : r.resolutionNote()));
    }

    public record RaiseRequest(
            @NotNull PaymentFlag.SubjectType subjectType,
            @Positive long subjectId,
            @NotBlank String note) {
    }

    public record ResolveRequest(String resolutionNote) {
    }

    public record FlagResponse(
            Long id, String subjectType, Long subjectId, String note, String status,
            Long flaggedBy, Instant flaggedAt, String resolutionNote, Long resolvedBy, Instant resolvedAt) {

        static FlagResponse from(PaymentFlag f) {
            return new FlagResponse(f.getId(), f.getSubjectType().name(), f.getSubjectId(), f.getNote(),
                    f.getStatus().name(), f.getFlaggedBy(), f.getFlaggedAt(),
                    f.getResolutionNote(), f.getResolvedBy(), f.getResolvedAt());
        }
    }
}
