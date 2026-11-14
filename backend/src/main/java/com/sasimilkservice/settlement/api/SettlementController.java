package com.sasimilkservice.settlement.api;

import com.sasimilkservice.paymentops.api.PaymentOpsDtos.ApproveRequest;
import com.sasimilkservice.paymentops.api.PaymentOpsDtos.BankBalanceCheckResponse;
import com.sasimilkservice.paymentops.api.PaymentOpsDtos.BankBalanceRequest;
import com.sasimilkservice.paymentops.api.PaymentOpsDtos.PreValidationResponse;
import com.sasimilkservice.paymentops.domain.BankBalanceCheck.SubjectType;
import com.sasimilkservice.paymentops.service.PaymentOpsService;
import com.sasimilkservice.producer.spi.ProducerDirectory;
import com.sasimilkservice.producer.spi.ProducerDirectory.ProducerView;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.AddAdjustmentRequest;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.CreateSettlementRequest;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.DailyCollectionRow;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.SettlementDetail;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.SettlementSummary;
import com.sasimilkservice.settlement.api.dto.SettlementDtos.StatusHistoryResponse;
import com.sasimilkservice.settlement.bill.SettlementBillService;
import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementItem;
import com.sasimilkservice.settlement.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final PaymentOpsService paymentOps;
    private final SettlementBillService bills;

    public SettlementController(SettlementService service, ProducerDirectory producers,
                               PaymentOpsService paymentOps, SettlementBillService bills) {
        this.service = service;
        this.producers = producers;
        this.paymentOps = paymentOps;
        this.bills = bills;
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
        Settlement s = service.create(r.periodStart(), r.periodEnd(), r.frequency(), r.regionId());
        return SettlementDetail.from(s, List.of());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','VERIFY_PAYMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')")
    public List<StatusHistoryResponse> history(@PathVariable long id) {
        return service.historyOf(id).stream().map(StatusHistoryResponse::from).toList();
    }

    @PostMapping("/{id}/bills/generate")
    @PreAuthorize("hasAnyAuthority('GENERATE_BILLS','GENERATE_SETTLEMENT','APPROVE_SETTLEMENT','VERIFY_PAYMENT')")
    public Map<String, Integer> generateBills(@PathVariable long id) {
        int generated = service.generateBills(id);
        return Map.of("generated", generated, "total", service.itemsOf(id).size());
    }

    private static final String VIEW_ANY =
            "hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','VERIFY_PAYMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')";

    /** One producer's bill. {@code ?download=true} forces a download; otherwise it opens inline as a preview. */
    @GetMapping("/{id}/items/{itemId}/bill")
    @PreAuthorize(VIEW_ANY)
    public ResponseEntity<byte[]> bill(@PathVariable long id, @PathVariable long itemId,
                                       @RequestParam(defaultValue = "false") boolean download) {
        return pdf(bills.billPdf(id, itemId), bills.billFileName(id, itemId) + ".pdf", download);
    }

    /** All producers' bills in one PDF. */
    @GetMapping("/{id}/bills")
    @PreAuthorize(VIEW_ANY)
    public ResponseEntity<byte[]> allBills(@PathVariable long id,
                                           @RequestParam(defaultValue = "true") boolean download) {
        return pdf(bills.allBillsPdf(id), service.require(id).getSettlementNumber() + "-bills.pdf", download);
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String fileName, boolean download) {
        ContentDisposition cd = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
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

    @GetMapping("/{id}/bank-balance")
    @PreAuthorize("hasAnyAuthority('VERIFY_PAYMENT','APPROVE_SETTLEMENT','PROCESS_PAYMENT','VIEW_PAYMENTS')")
    public BankBalanceCheckResponse bankBalance(@PathVariable long id) {
        return paymentOps.latest(SubjectType.SETTLEMENT, id)
                .map(BankBalanceCheckResponse::from)
                .orElse(null);
    }

    @PostMapping("/{id}/bank-balance")
    @PreAuthorize("hasAnyAuthority('VERIFY_PAYMENT','APPROVE_SETTLEMENT','PROCESS_PAYMENT')")
    public BankBalanceCheckResponse verifyBankBalance(@PathVariable long id,
                                                     @Valid @RequestBody BankBalanceRequest r) {
        Settlement s = service.require(id);
        return BankBalanceCheckResponse.from(paymentOps.record(SubjectType.SETTLEMENT, id,
                r.centerBankAccountId(), r.bankAccountLabel(), r.availableBalance(),
                s.getTotalNetPayable(), r.method(), r.note()));
    }

    @GetMapping("/{id}/pre-validation")
    @PreAuthorize("hasAnyAuthority('GENERATE_SETTLEMENT','REVIEW_SETTLEMENT','VERIFY_PAYMENT','APPROVE_SETTLEMENT','VIEW_PAYMENTS')")
    public PreValidationResponse preValidation(@PathVariable long id) {
        return PreValidationResponse.from(service.preValidate(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public SettlementDetail approve(@PathVariable long id,
                                   @RequestBody(required = false) ApproveRequest r) {
        service.approve(id, r == null || r.confirmed() == null || r.confirmed());
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
        return SettlementDetail.from(s, items, lookup, service.bankSubmissionOf(s.getId()).orElse(null));
    }

    public record AdjustmentResponse(Long id, Long producerId, String type,
                                     java.math.BigDecimal amount, String reason, String status) {
    }
}
