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
    private final com.sasimilkservice.report.service.StatementPdfRenderer statementPdf;

    public ReportController(ReportService reports, ReportExporter exporter,
                            com.sasimilkservice.report.service.StatementPdfRenderer statementPdf) {
        this.reports = reports;
        this.exporter = exporter;
        this.statementPdf = statementPdf;
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

    @GetMapping("/payment-dashboard")
    @PreAuthorize("hasAnyAuthority('VIEW_PAYMENTS','VIEW_REPORTS','GENERATE_REPORTS','VIEW_BASIC_REPORTS','EXPORT_REPORTS')")
    public ReportService.PaymentDashboard paymentDashboard(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return reports.paymentDashboard(from, to);
    }

    /** pending | bank-submissions | confirmations | incentives-deductions | period-summary */
    @GetMapping("/payments/{kind}")
    @PreAuthorize("hasAnyAuthority('VIEW_PAYMENTS','VIEW_REPORTS','GENERATE_REPORTS','VIEW_BASIC_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> paymentReport(@PathVariable String kind, @RequestParam LocalDate from,
                                           @RequestParam LocalDate to,
                                           @RequestParam(defaultValue = "pdf") String format) {
        return export(reports.paymentReport(kind, from, to), format, "payment-" + kind + "-" + from + "-" + to);
    }

    /** Consolidated producer-wise summary of one payment batch — the sheet used for the manual bank submission. */
    @GetMapping("/settlement/{settlementId}/summary")
    public ResponseEntity<?> settlementSummary(@PathVariable long settlementId,
                                               @RequestParam(defaultValue = "pdf") String format,
                                               @RequestParam(required = false) Long regionId,
                                               @RequestParam(required = false) String sort) {
        ReportService.BatchHeader h = reports.batchHeader(settlementId);
        List<ReportService.BatchLine> lines = reports.batchLines(settlementId, regionId, sort);
        java.math.BigDecimal litres = java.math.BigDecimal.ZERO, milk = litres, inc = litres, ded = litres,
                adv = litres, adj = litres, net = litres;
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (ReportService.BatchLine l : lines) {
            litres = litres.add(l.litres());
            milk = milk.add(l.milkAmount());
            inc = inc.add(l.incentive());
            ded = ded.add(l.deductions());
            adv = adv.add(l.advanceRecovery());
            adj = adj.add(l.adjustments());
            net = net.add(l.netPayable());
            rows.add(java.util.Arrays.<Object>asList(l.producerNumber(), l.producerName(), l.region(),
                    l.litres(), l.milkAmount(), l.incentive(), l.deductions(), l.advanceRecovery(),
                    l.adjustments(), l.netPayable(), l.billNumber() == null ? "" : l.billNumber(),
                    friendlyStatus(l.paymentStatus()), l.utr() == null ? "" : l.utr()));
        }
        rows.add(java.util.Arrays.<Object>asList("TOTAL", "", "", litres, milk, inc, ded, adv, adj, net,
                "", "", ""));
        String kind = switch (h.frequency()) {
            case "WEEKLY" -> "Weekly";
            case "MONTHLY" -> "Monthly";
            default -> "Payment";
        };
        String sub = "%s · %s to %s · %d producers · %d bills · %s%s".formatted(
                h.number(), h.periodStart(), h.periodEnd(), h.producers(), h.bills(),
                h.submittedOn() == null ? "Not yet submitted to bank"
                        : "Submitted to bank on " + h.submittedOn()
                        + (h.bankReference() == null ? "" : " (ref " + h.bankReference() + ")"),
                " · Status: " + h.status().replace('_', ' ')
                + (regionId == null ? "" : " · Area filter applied"));
        TabularReport report = TabularReport.of(kind + " Farmer Payment Summary", sub,
                List.of("Producer ID", "Name", "Region", "Litres", "Milk Amount", "Incentive", "Deductions",
                        "Advance Recovery", "Adjustments", "Net Payable", "Bill No.", "Payment Status", "Bank Ref (UTR)"),
                rows);
        return export(report, format, h.number() + "-summary");
    }

    private static String friendlyStatus(String s) {
        return switch (s) {
            case "PENDING" -> "Pending";
            case "INITIATED", "PROCESSING" -> "Submitted to bank";
            case "SUCCESS" -> "Paid";
            case "FAILED" -> "Failed";
            case "ON_HOLD" -> "On hold";
            case "CANCELLED" -> "Cancelled";
            default -> s;
        };
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
        ReportService.DetailedStatement d = reports.detailedStatement(producerId, from, to);
        String base = "statement-" + d.producerNumber() + "-" + from + "-to-" + to;
        if ("pdf".equalsIgnoreCase(format)) {
            return file(statementPdf.render(reports.centerLine(), d), base + ".pdf", "application/pdf");
        }
        return export(statementTable(d), format, base);
    }

    /** Flat version for Excel / CSV: one row per collection, then the totals and money summary. */
    private static TabularReport statementTable(ReportService.DetailedStatement d) {
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (ReportService.StatementCollection c : d.collections()) {
            rows.add(java.util.Arrays.<Object>asList(c.date().toString(),
                    "MORNING".equals(c.shift()) ? "Morning" : "Evening", c.recordedAt(), c.collectionNumber(),
                    c.litres(), c.fat(), c.snf(), c.rate(), c.milkAmount(), c.incentive(), c.total()));
        }
        rows.add(java.util.Arrays.<Object>asList("TOTAL", "", "", "", d.totalLitres(), null, null, d.averageRate(),
                d.milkAmount(), d.incentive(), d.gross()));
        rows.add(java.util.Arrays.<Object>asList("", "", "", "", null, null, null, null, null, null, null));
        rows.add(java.util.Arrays.<Object>asList("Morning litres", "", "", "", d.morningLitres(), null, null, null, null, null, null));
        rows.add(java.util.Arrays.<Object>asList("Evening litres", "", "", "", d.eveningLitres(), null, null, null, null, null, null));
        rows.add(java.util.Arrays.<Object>asList("Gross earnings", "", "", "", null, null, null, null, null, null, d.gross()));
        for (ReportService.StatementCharge c : d.deductions()) {
            rows.add(java.util.Arrays.<Object>asList(c.name() + " deduction", "", "", "", null, null, null, null, null, null, c.amount().negate()));
        }
        rows.add(java.util.Arrays.<Object>asList("Advance taken in period", "", "", "", null, null, null, null, null, null, d.advanceTaken()));
        rows.add(java.util.Arrays.<Object>asList("Advance recovered in period", "", "", "", null, null, null, null, null, null, d.advanceRecovered().negate()));
        rows.add(java.util.Arrays.<Object>asList("Advance outstanding today", "", "", "", null, null, null, null, null, null, d.advanceOutstanding()));
        for (ReportService.StatementPayment p : d.payments()) {
            rows.add(java.util.Arrays.<Object>asList("Payment " + p.batch(), p.periodStart() + " to " + p.periodEnd(),
                    p.billNumber() == null ? "" : p.billNumber(), p.status(), null, null, null, null, p.milk(),
                    p.incentive(), p.netPayable()));
        }
        return TabularReport.of("Milk Collection Statement",
                "%s (%s) · %s%s · %s to %s".formatted(d.producerName(), d.producerNumber(),
                        d.region() == null ? "" : "Region " + d.region() + " · ", d.collectionDays() + " collection days",
                        d.from(), d.to()),
                List.of("Date", "Shift", "Time", "Collection No.", "Litres", "FAT", "SNF", "Rate / L",
                        "Milk Amount", "Incentive", "Total"),
                rows);
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
