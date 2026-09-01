package com.sasimilkservice.report.api;

import java.util.List;

/** Minimal RFC-4180 CSV builder for report exports (PDF/Excel are a later phase). */
final class CsvWriter {

    private CsvWriter() {}

    static String of(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(row(header.stream().map(Object.class::cast).toList()));
        for (List<Object> r : rows) {
            sb.append(row(r));
        }
        return sb.toString();
    }

    private static String row(List<Object> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    private static String escape(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
