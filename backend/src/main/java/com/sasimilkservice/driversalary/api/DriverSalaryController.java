package com.sasimilkservice.driversalary.api;

import com.sasimilkservice.driver.repository.DriverRepository;
import com.sasimilkservice.driver.domain.Driver;
import com.sasimilkservice.driversalary.domain.DriverSalaryBatch;
import com.sasimilkservice.driversalary.domain.DriverSalaryItem;
import com.sasimilkservice.driversalary.domain.DriverSalaryItem.PaymentMethod;
import com.sasimilkservice.driversalary.service.DriverSalaryService;
import com.sasimilkservice.driversalary.service.DriverSalaryService.ItemComponents;
import com.sasimilkservice.report.export.ReportExporter;
import com.sasimilkservice.report.export.TabularReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/salary-batches")
public class DriverSalaryController {

    private final DriverSalaryService service;
    private final DriverRepository drivers;
    private final ReportExporter exporter;

    public DriverSalaryController(DriverSalaryService service, DriverRepository drivers, ReportExporter exporter) {
        this.service = service;
        this.drivers = drivers;
        this.exporter = exporter;
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAnyAuthority('VIEW_PAYMENTS','VIEW_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> report(@PathVariable long id,
                                    @org.springframework.web.bind.annotation.RequestParam(defaultValue = "json") String format) {
        DriverSalaryBatch b = service.require(id);
        List<DriverSalaryItem> items = service.items(id);
        Map<Long, Driver> byId = drivers.findAll().stream().collect(Collectors.toMap(Driver::getId, Function.identity()));

        if (format == null || format.isBlank() || "json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(BatchDetail.from(b, items));
        }
        TabularReport report = TabularReport.of(
                "Driver Salary — " + b.getBatchNumber(),
                b.getPeriodMonth().toString().substring(0, 7),
                List.of("Driver", "Basic", "Working Days", "Allowances", "Overtime", "Gross",
                        "Advance", "Loan", "Penalty", "Other Ded.", "Net Salary", "Status", "Method", "Reference"),
                items.stream().<List<Object>>map(i -> {
                    Driver d = byId.get(i.getDriverId());
                    return java.util.Arrays.<Object>asList(
                            d == null ? ("#" + i.getDriverId()) : (d.getName() + " (" + d.getCode() + ")"),
                            i.getBasicSalary(), i.getWorkingDays(), i.getAllowances(), i.getOvertime(),
                            i.getGrossAmount(), i.getSalaryAdvance(), i.getLoanRecovery(), i.getPenalty(),
                            i.getOtherDeduction(), i.getNetSalary(), i.getPaymentStatus().name(),
                            i.getPaymentMethod() == null ? "" : i.getPaymentMethod().name(),
                            i.getPaymentReference() == null ? "" : i.getPaymentReference());
                }).toList());
        String base = "driver-salary-" + b.getBatchNumber();
        byte[] body = switch (format.toLowerCase()) {
            case "csv" -> exporter.csv(report);
            case "xlsx", "excel" -> exporter.xlsx(report);
            case "pdf" -> exporter.pdf(report, "SASI MILK SERVICE");
            default -> throw new com.sasimilkservice.common.error.BusinessRuleException("Unsupported format: " + format);
        };
        String ct = switch (format.toLowerCase()) {
            case "csv" -> "text/csv";
            case "pdf" -> "application/pdf";
            default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };
        String ext = format.equalsIgnoreCase("excel") ? "xlsx" : format.toLowerCase();
        return ResponseEntity.ok()
                .headers(h -> h.setContentDisposition(ContentDisposition.attachment().filename(base + "." + ext).build()))
                .contentType(MediaType.parseMediaType(ct))
                .body(body);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_PAYMENTS','GENERATE_SETTLEMENT')")
    public List<BatchSummary> list() {
        return service.list().stream().map(BatchSummary::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_PAYMENTS','GENERATE_SETTLEMENT')")
    public BatchDetail get(@PathVariable long id) {
        return BatchDetail.from(service.require(id), service.items(id));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('VIEW_PAYMENTS')")
    public List<DriverSalaryService.SalaryPaymentRow> paymentHistory() {
        return service.paymentHistory();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public BatchDetail create(@Valid @RequestBody CreateBatchRequest r) {
        DriverSalaryBatch b = service.createBatch(r.month());
        return BatchDetail.from(b, service.items(b.getId()));
    }

    @PostMapping("/{id}/calculate")
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public BatchDetail calculate(@PathVariable long id) {
        service.calculate(id);
        return BatchDetail.from(service.require(id), service.items(id));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('REVIEW_SETTLEMENT')")
    public BatchDetail review(@PathVariable long id) {
        service.review(id);
        return BatchDetail.from(service.require(id), service.items(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public BatchDetail approve(@PathVariable long id) {
        service.approve(id);
        return BatchDetail.from(service.require(id), service.items(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('APPROVE_SETTLEMENT')")
    public BatchDetail cancel(@PathVariable long id) {
        service.cancel(id);
        return BatchDetail.from(service.require(id), service.items(id));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('GENERATE_SETTLEMENT')")
    public ItemResponse updateItem(@PathVariable long itemId, @RequestBody ItemComponents body) {
        return ItemResponse.from(service.updateItem(itemId, body));
    }

    @PostMapping("/items/{itemId}/pay")
    @PreAuthorize("hasAuthority('PROCESS_PAYMENT')")
    public ItemResponse pay(@PathVariable long itemId, @Valid @RequestBody PayRequest r) {
        return ItemResponse.from(service.pay(itemId, r.method(), r.reference(), r.note()));
    }

    @PostMapping("/items/{itemId}/fail")
    @PreAuthorize("hasAuthority('PROCESS_PAYMENT')")
    public ItemResponse fail(@PathVariable long itemId, @Valid @RequestBody FailRequest r) {
        return ItemResponse.from(service.markFailed(itemId, r.reason()));
    }

    @PostMapping("/items/{itemId}/retry")
    @PreAuthorize("hasAuthority('PROCESS_PAYMENT')")
    public ItemResponse retry(@PathVariable long itemId) {
        return ItemResponse.from(service.retry(itemId));
    }

    // ----------------------------------------------------------------- DTOs

    public record CreateBatchRequest(@NotNull LocalDate month) {
    }

    public record PayRequest(PaymentMethod method, String reference, String note) {
    }

    public record FailRequest(@NotBlank String reason) {
    }

    public record BatchSummary(
            Long id, String batchNumber, String batchType, LocalDate periodMonth, String status,
            int totalDrivers, BigDecimal totalGross, BigDecimal totalDeductions, BigDecimal totalNet,
            BigDecimal totalPaid, Instant approvedAt) {

        static BatchSummary from(DriverSalaryBatch b) {
            return new BatchSummary(b.getId(), b.getBatchNumber(), b.getBatchType(), b.getPeriodMonth(),
                    b.getStatus().name(), b.getTotalDrivers(), b.getTotalGross(), b.getTotalDeductions(),
                    b.getTotalNet(), b.getTotalPaid(), b.getApprovedAt());
        }
    }

    public record BatchDetail(
            Long id, String batchNumber, String batchType, LocalDate periodMonth, String status,
            int totalDrivers, BigDecimal totalGross, BigDecimal totalAllowances, BigDecimal totalOvertime,
            BigDecimal totalDeductions, BigDecimal totalNet, BigDecimal totalPaid,
            Instant calculatedAt, Instant reviewedAt, Instant approvedAt, Instant cancelledAt,
            List<ItemResponse> items) {

        static BatchDetail from(DriverSalaryBatch b, List<DriverSalaryItem> items) {
            return new BatchDetail(b.getId(), b.getBatchNumber(), b.getBatchType(), b.getPeriodMonth(),
                    b.getStatus().name(), b.getTotalDrivers(), b.getTotalGross(), b.getTotalAllowances(),
                    b.getTotalOvertime(), b.getTotalDeductions(), b.getTotalNet(), b.getTotalPaid(),
                    b.getCalculatedAt(), b.getReviewedAt(), b.getApprovedAt(), b.getCancelledAt(),
                    items.stream().map(ItemResponse::from).toList());
        }
    }

    public record ItemResponse(
            Long id, Long batchId, Long driverId, Long vehicleId, String salaryType,
            BigDecimal basicSalary, BigDecimal workingDays, BigDecimal allowances, BigDecimal overtime,
            BigDecimal otherEarnings, BigDecimal salaryAdvance, BigDecimal loanRecovery, BigDecimal penalty,
            BigDecimal otherDeduction, BigDecimal grossAmount, BigDecimal totalDeduction, BigDecimal netSalary,
            String paymentStatus, String paymentMethod, String paymentReference, Instant paidAt,
            String failureReason, String note) {

        static ItemResponse from(DriverSalaryItem i) {
            return new ItemResponse(i.getId(), i.getBatchId(), i.getDriverId(), i.getVehicleId(), i.getSalaryType(),
                    i.getBasicSalary(), i.getWorkingDays(), i.getAllowances(), i.getOvertime(),
                    i.getOtherEarnings(), i.getSalaryAdvance(), i.getLoanRecovery(), i.getPenalty(),
                    i.getOtherDeduction(), i.getGrossAmount(), i.getTotalDeduction(), i.getNetSalary(),
                    i.getPaymentStatus().name(), i.getPaymentMethod() == null ? null : i.getPaymentMethod().name(),
                    i.getPaymentReference(), i.getPaidAt(), i.getFailureReason(), i.getNote());
        }
    }
}
