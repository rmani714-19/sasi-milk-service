package com.sasimilkservice.settlement.api;

import com.sasimilkservice.settlement.api.dto.SettlementDtos.AddAdjustmentRequest;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.CreateSettlementRequest;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.SettlementDetail;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.SettlementSummary;
import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementService service;

    public SettlementController(SettlementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')")
    public List<SettlementSummary> list() {
        return service.findAll().stream().map(SettlementSummary::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')")
    public SettlementDetail get(@PathVariable long id) {
        Settlement s = service.require(id);
        return SettlementDetail.from(s, service.itemsOf(s.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public SettlementDetail create(@Valid @RequestBody CreateSettlementRequest r) {
        Settlement s = service.create(r.periodStart(), r.periodEnd());
        return SettlementDetail.from(s, List.of());
    }

    @PostMapping("/{id}/calculate")
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public SettlementDetail calculate(@PathVariable long id) {
        Settlement s = service.calculate(id);
        return SettlementDetail.from(s, service.itemsOf(s.getId()));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('REVIEW_SETTLEMENT')")
    public SettlementDetail review(@PathVariable long id) {
        Settlement s = service.review(id);
        return SettlementDetail.from(s, service.itemsOf(s.getId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public SettlementDetail approve(@PathVariable long id) {
        Settlement s = service.approve(id);
        return SettlementDetail.from(s, service.itemsOf(s.getId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public SettlementDetail cancel(@PathVariable long id) {
        Settlement s = service.cancel(id);
        return SettlementDetail.from(s, service.itemsOf(s.getId()));
    }

    @PostMapping("/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT')")
    public AdjustmentResponse addAdjustment(@PathVariable long id,
                                            @Valid @RequestBody AddAdjustmentRequest r) {
        var a = service.addAdjustment(id, r.producerId(), r.type(), r.amount(), r.reason(),
                r.originSettlementId());
        return new AdjustmentResponse(a.getId(), a.getProducerId(), a.getAdjustmentType().name(),
                a.getAmount(), a.getReason(), a.getStatus().name());
    }

    public record AdjustmentResponse(Long id, Long producerId, String type,
                                     java.math.BigDecimal amount, String reason, String status) {
    }
}
