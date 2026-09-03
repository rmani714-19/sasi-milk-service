package com.sasimilkservice.settlement.api;

import com.sasimilkservice.producer.spi.ProducerDirectory;
import com.sasimilkservice.producer.spi.ProducerDirectory.ProducerView;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.AddAdjustmentRequest;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.CreateSettlementRequest;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.DailyCollectionRow;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.SettlementDetail;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.SettlementSummary;
import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementItem;
import com.sasimilkservice.settlement.service.SettlementService;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementService service;
    private final ProducerDirectory producers;

    public SettlementController(SettlementService service, ProducerDirectory producers) {
        this.service = service;
        this.producers = producers;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','VERIFY_PAYMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')")
    public List<SettlementSummary> list(@RequestParam(required = false) String frequency) {
        return service.findAll().stream()
                .filter(s -> frequency == null || frequency.isBlank()
                        || s.getPaymentFrequency().name().equalsIgnoreCase(frequency))
                .map(SettlementSummary::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','VERIFY_PAYMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')")
    public SettlementDetail get(@PathVariable long id) {
        return detail(service.require(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public SettlementDetail create(@Valid @RequestBody CreateSettlementRequest r) {
        Settlement s = service.create(r.periodStart(), r.periodEnd(), r.frequency());
        return SettlementDetail.from(s, List.of());
    }

    @PostMapping("/{id}/calculate")
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public SettlementDetail calculate(@PathVariable long id) {
        service.calculate(id);
        return detail(service.require(id));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('REVIEW_SETTLEMENT')")
    public SettlementDetail review(@PathVariable long id) {
        service.review(id);
        return detail(service.require(id));
    }

    @PostMapping("/{id}/items/{itemId}/verify")
    @PreAuthorize("hasAuthority('VERIFY_PAYMENT')")
    public SettlementDetail verifyItem(@PathVariable long id, @PathVariable long itemId) {
        service.verifyItem(id, itemId);
        return detail(service.require(id));
    }

    @PostMapping("/{id}/items/{itemId}/unverify")
    @PreAuthorize("hasAuthority('VERIFY_PAYMENT')")
    public SettlementDetail unverifyItem(@PathVariable long id, @PathVariable long itemId) {
        service.unverifyItem(id, itemId);
        return detail(service.require(id));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('VERIFY_PAYMENT')")
    public SettlementDetail verifyAll(@PathVariable long id) {
        service.verifyAll(id);
        return detail(service.require(id));
    }

    @GetMapping("/{id}/items/{itemId}/collections")
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','VERIFY_PAYMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')")
    public List<DailyCollectionRow> itemCollections(@PathVariable long id, @PathVariable long itemId) {
        return service.collectionsForItem(id, itemId).stream().map(DailyCollectionRow::from).toList();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public SettlementDetail approve(@PathVariable long id) {
        service.approve(id);
        return detail(service.require(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public SettlementDetail cancel(@PathVariable long id) {
        service.cancel(id);
        return detail(service.require(id));
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

    private SettlementDetail detail(Settlement s) {
        List<SettlementItem> items = service.itemsOf(s.getId());
        Map<Long, ProducerView> lookup = items.stream()
                .map(SettlementItem::getProducerId)
                .distinct()
                .map(producers::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toMap(ProducerView::id, Function.identity(), (a, b) -> a));
        return SettlementDetail.from(s, items, lookup);
    }

    public record AdjustmentResponse(Long id, Long producerId, String type,
                                     java.math.BigDecimal amount, String reason, String status) {
    }
}
