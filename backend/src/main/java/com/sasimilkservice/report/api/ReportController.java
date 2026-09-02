package com.sasimilkservice.report.api;

import com.sasimilkservice.report.export.ReportExporter;
import com.sasimilkservice.report.export.TabularReport;
import com.sasimilkservice.report.service.ReportService;
import com.sasimilkservice.report.service.ReportService.DailyCollectionRow;
import com.sasimilkservice.report.service.ReportService.PaymentRow;
import com.sasimilkservice.report.service.ReportService.ProducerStatement;
import com.sasimilkservice.report.service.ReportService.RegionRow;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyAuthority('VIEW_REPORTS','GENERATE_REPORTS','VIEW_BASIC_REPORTS','EXPORT_REPORTS')")
public class ReportController {

    private final ReportService reports;
    private final ReportExporter exporter;

    public ReportController(ReportService reports, ReportExporter exporter) {
        this.reports = reports;
        this.exporter = exporter;
    }

    @GetMapping("/daily-collection")
    public ResponseEntity<?> dailyCollection(@RequestParam LocalDate from,
                                             @RequestParam LocalDate to,
                                             @RequestParam(defaultValue = "json") String format) {
        List<DailyCollectionRow> rows = reports.dailyCollection(from, to);
        if (isJson(format)) {
            return ResponseEntity.ok(rows);
        }
        TabularReport report = TabularReport.of(
                "Daily Collection Report", "%s to %s".formatted(from, to),
                List.of("Date", "Morning Litres", "Evening Litres", "Total Litres",
                        "Farmers", "Collections", "Average Rate", "Total Amount"),
                rows.stream().<List<Object>>map(r -> List.of(r.date().toString(), r.morningLitres(),
                        r.eveningLitres(), r.totalLitres(), r.producerCount(), r.collectionCount(),
                        r.averageRate(), r.totalAmount())).toList());
        return export(report, format, "daily-collection");
    }

    @GetMapping("/region")
    public ResponseEntity<?> region(@RequestParam LocalDate from,
                                    @RequestParam LocalDate to,
                                    @RequestParam(defaultValue = "json") String format) {
        List<RegionRow> rows = reports.regionReport(from, to);
        if (isJson(format)) {
            return ResponseEntity.ok(rows);
        }
        TabularReport report = TabularReport.of(
                "Region Report", "%s to %s".formatted(from, to),
                List.of("Region", "Name", "Farmers", "Total Litres", "Average Rate", "Total Amount"),
                rows.stream().<List<Object>>map(r -> List.of(r.regionCode(), r.regionName(),
                        r.producerCount(), r.totalLitres(), r.averageRate(), r.totalAmount())).toList());
        return export(report, format, "region-report");
    }

    @GetMapping("/settlement/{settlementId}/payments")
    public ResponseEntity<?> settlementPayments(@PathVariable long settlementId,
                                                @RequestParam(defaultValue = "json") String format) {
        List<PaymentRow> rows = reports.settlementPayments(settlementId);
        if (isJson(format)) {
            return ResponseEntity.ok(rows);
        }
        TabularReport report = TabularReport.of(
                "Settlement Payment Report", "Settlement #" + settlementId,
                List.of("Farmer", "Total Litres", "Gross Amount", "Total Deduction", "Advance Recovery",
                        "Net Payable", "Payment Status", "UTR", "Payout Status", "Provider"),
                rows.stream().<List<Object>>map(r -> java.util.Arrays.<Object>asList(r.producerId(),
                        r.totalLitres(), r.grossAmount(), r.totalDeduction(), r.advanceRecovery(),
                        r.netPayable(), r.paymentStatus(), r.utr() == null ? "" : r.utr(),
                        r.payoutStatus() == null ? "" : r.payoutStatus(),
                        r.provider() == null ? "" : r.provider())).toList());
        return export(report, format, "settlement-" + settlementId + "-payments");
    }

    @GetMapping("/producer-statement")
    public ResponseEntity<?> producerStatement(@RequestParam long producerId,
                                               @RequestParam LocalDate from,
                                               @RequestParam LocalDate to,
                                               @RequestParam(defaultValue = "json") String format) {
        ProducerStatement s = reports.producerStatement(producerId, from, to);
        if (isJson(format)) {
            return ResponseEntity.ok(s);
        }
        TabularReport report = TabularReport.of(
                "Farmer Statement", "Farmer #%d · %s to %s".formatted(producerId, from, to),
                List.of("Field", "Value"),
                List.of(
                        List.of("Total litres", s.totalLitres()),
                        List.of("Milk value", s.totalMilkAmount()),
                        List.of("Incentives", s.totalIncentive()),
                        List.of("Collections", s.collectionCount()),
                        List.of("Deductions", s.totalDeductions()),
                        List.of("Advance taken", s.totalAdvance()),
                        List.of("Advance recovered", s.advanceRecovered()),
                        List.of("Advance outstanding", s.advanceOutstanding()),
                        List.of("Total paid", s.totalPaid())));
        return export(report, format, "farmer-" + producerId + "-statement");
    }

    // ---- dispatch ----

    private static boolean isJson(String format) {
        return format == null || format.isBlank() || "json".equalsIgnoreCase(format);
    }

    private ResponseEntity<byte[]> export(TabularReport report, String format, String baseName) {
        return switch (format.toLowerCase()) {
            case "csv" -> file(exporter.csv(report), baseName + ".csv", "text/csv");
            case "xlsx", "excel" -> file(exporter.xlsx(report), baseName + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "pdf" -> file(exporter.pdf(report, reports.centerName()), baseName + ".pdf", "application/pdf");
            default -> throw new com.sasimilkservice.common.error.BusinessRuleException(
                    "Unsupported report format: " + format);
        };
    }

    private static ResponseEntity<byte[]> file(byte[] body, String filename, String contentType) {
        return ResponseEntity.ok()
                .headers(h -> h.setContentDisposition(
                        ContentDisposition.attachment().filename(filename).build()))
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }
}
