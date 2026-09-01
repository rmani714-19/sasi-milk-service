package com.sasimilkservice.deduction.api;

import com.sasimilkservice.deduction.domain.DeductionTransaction;
import com.sasimilkservice.deduction.domain.DeductionType;
import com.sasimilkservice.deduction.service.DeductionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/deductions")
public class DeductionController {

    private final DeductionService service;

    public DeductionController(DeductionService service) {
        this.service = service;
    }

    @GetMapping("/types")
    @PreAuthorize("hasAnyAuthority('MANAGE_DEDUCTIONS','VIEW_DASHBOARD')")
    public List<TypeResponse> types() {
        return service.listTypes().stream().map(TypeResponse::from).toList();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_DEDUCTIONS','VIEW_DASHBOARD')")
    public List<DeductionResponse> list(@RequestParam long producerId) {
        return service.listForProducer(producerId).stream().map(DeductionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_DEDUCTIONS')")
    public DeductionResponse create(@Valid @RequestBody CreateDeductionRequest r) {
        return DeductionResponse.from(service.create(r.producerId(), r.typeCode(), r.amount(),
                r.txnDate(), r.description()));
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MANAGE_DEDUCTIONS')")
    public void cancel(@PathVariable long id) {
        service.cancel(id);
    }

    public record CreateDeductionRequest(
            @Positive long producerId,
            @NotBlank String typeCode,
            @NotNull @DecimalMin("0.0") BigDecimal amount,
            LocalDate txnDate,
            String description) {
    }

    public record TypeResponse(Long id, String code, String name, boolean affectsLedger, String status) {
        static TypeResponse from(DeductionType t) {
            return new TypeResponse(t.getId(), t.getCode(), t.getName(), t.isAffectsLedger(),
                    t.getStatus().name());
        }
    }

    public record DeductionResponse(
            Long id, Long producerId, Long deductionTypeId, BigDecimal amount,
            LocalDate txnDate, String description, String status, Long settlementItemId) {

        static DeductionResponse from(DeductionTransaction d) {
            return new DeductionResponse(d.getId(), d.getProducerId(), d.getDeductionTypeId(),
                    d.getAmount(), d.getTxnDate(), d.getDescription(), d.getStatus().name(),
                    d.getSettlementItemId());
        }
    }
}
