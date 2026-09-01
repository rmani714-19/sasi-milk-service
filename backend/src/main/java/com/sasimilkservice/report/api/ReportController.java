package com.sasimilkservice.report.api;

import com.sasimilkservice.report.service.ReportService;
import com.sasimilkservice.report.service.ReportService.DailyCollectionRow;
import com.sasimilkservice.report.service.ReportService.PaymentRow;
import com.sasimilkservice.report.service.ReportService.RegionRow;
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

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/daily-collection")
    public ResponseEntity<?> dailyCollection(@RequestParam LocalDate from,
                                             @RequestParam LocalDate to,
                                             @RequestParam(defaultValue = "json") String format) {
        List<DailyCollectionRow> rows = reports.dailyCollection(from, to);
        if ("csv".equalsIgnoreCase(format)) {
            String csv = CsvWriter.of(
                    List.of("Date", "Morning Litres", "Evening Litres", "Total Litres",
                            "Producer Count", "Collection Count", "Average Rate", "Total Amount"),
                    rows.stream().<List<Object>>map(r -> List.of(r.date(), r.morningLitres(),
                            r.eveningLitres(), r.totalLitres(), r.producerCount(),
                            r.collectionCount(), r.averageRate(), r.totalAmount())).toList());
            return csvResponse(csv, "daily-collection");
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/region")
    public ResponseEntity<?> region(@RequestParam LocalDate from,
                                    @RequestParam LocalDate to,
                                    @RequestParam(defaultValue = "json") String format) {
        List<RegionRow> rows = reports.regionReport(from, to);
        if ("csv".equalsIgnoreCase(format)) {
            String csv = CsvWriter.of(
                    List.of("Region", "Name", "Producer Count", "Total Litres", "Average Rate", "Total Amount"),
                    rows.stream().<List<Object>>map(r -> List.of(r.regionCode(), r.regionName(),
                            r.producerCount(), r.totalLitres(), r.averageRate(), r.totalAmount())).toList());
            return csvResponse(csv, "region-report");
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/settlement/{settlementId}/payments")
    public ResponseEntity<?> settlementPayments(@PathVariable long settlementId,
                                                @RequestParam(defaultValue = "json") String format) {
        List<PaymentRow> rows = reports.settlementPayments(settlementId);
        if ("csv".equalsIgnoreCase(format)) {
            String csv = CsvWriter.of(
                    List.of("Producer", "Total Litres", "Gross Amount", "Total Deduction",
                            "Advance Recovery", "Net Payable", "Payment Status", "UTR",
                            "Payout Status", "Provider"),
                    rows.stream().<List<Object>>map(r -> List.of(r.producerId(), r.totalLitres(),
                            r.grossAmount(), r.totalDeduction(), r.advanceRecovery(), r.netPayable(),
                            r.paymentStatus(), r.utr() == null ? "" : r.utr(),
                            r.payoutStatus() == null ? "" : r.payoutStatus(),
                            r.provider() == null ? "" : r.provider())).toList());
            return csvResponse(csv, "settlement-" + settlementId + "-payments");
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/producer-statement")
    public ResponseEntity<?> producerStatement(@RequestParam long producerId,
                                               @RequestParam LocalDate from,
                                               @RequestParam LocalDate to) {
        return ResponseEntity.ok(reports.producerStatement(producerId, from, to));
    }

    private static ResponseEntity<String> csvResponse(String csv, String name) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + name + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
