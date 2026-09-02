package com.sasimilkservice.report.export;

import java.util.List;

/**
 * A report as a title + column headers + rows, ready to render to CSV / XLSX / PDF.
 * Cell values may be String, number, {@link java.time.LocalDate}, etc.
 */
public record TabularReport(
        String title,
        String subtitle,
        List<String> headers,
        List<List<Object>> rows) {

    public static TabularReport of(String title, String subtitle, List<String> headers, List<List<Object>> rows) {
        return new TabularReport(title, subtitle, headers, rows);
    }
}
