package com.sasimilkservice.report.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Renders a {@link TabularReport} to CSV, XLSX or a print-quality PDF. */
@Component
public class ReportExporter {

    private static final Logger log = LoggerFactory.getLogger(ReportExporter.class);
    private static final Color BRAND_GREEN = new Color(0x2F, 0x7D, 0x33);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    /** Brand logo bundled at src/main/resources/report-logo.png (null when absent). */
    private static final byte[] LOGO = loadLogo();

    private static byte[] loadLogo() {
        try (InputStream in = ReportExporter.class.getClassLoader().getResourceAsStream("report-logo.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            log.warn("report-logo.png could not be read; exports will have no logo", e);
            return null;
        }
    }

    // ---------------------------------------------------------------- CSV

    public byte[] csv(TabularReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvRow(r.headers().stream().map(Object.class::cast).toList()));
        for (List<Object> row : r.rows()) {
            sb.append(csvRow(row));
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String csvRow(List<Object> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String s = cells.get(i) == null ? "" : cells.get(i).toString();
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                s = '"' + s.replace("\"", "\"\"") + '"';
            }
            sb.append(s);
        }
        return sb.append("\r\n").toString();
    }

    // ---------------------------------------------------------------- XLSX

    public byte[] xlsx(TabularReport r) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Report");
            int col = r.headers().size();

            org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);

            org.apache.poi.ss.usermodel.Font headFont = wb.createFont();
            headFont.setBold(true);
            headFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headStyle = wb.createCellStyle();
            headStyle.setFont(headFont);
            headStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headStyle.setAlignment(HorizontalAlignment.CENTER);
            headStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle numStyle = wb.createCellStyle();
            numStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int rowIdx = 0;

            // brand letterhead — floating logo pinned top-left, text starts below it
            if (LOGO != null) {
                try {
                    int picIdx = wb.addPicture(LOGO, Workbook.PICTURE_TYPE_PNG);
                    ClientAnchor anchor = wb.getCreationHelper().createClientAnchor();
                    anchor.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);
                    anchor.setCol1(0);
                    anchor.setRow1(0);
                    anchor.setCol2(2);
                    anchor.setRow2(4);
                    sheet.createDrawingPatriarch().createPicture(anchor, picIdx);
                    for (int i = 0; i < 4; i++) {
                        sheet.createRow(i).setHeightInPoints(16);
                    }
                    rowIdx = 4;
                } catch (Exception e) {
                    log.warn("could not embed logo in xlsx", e);
                }
            }

            int titleRow = rowIdx;
            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue(r.title());
            title.getCell(0).setCellStyle(titleStyle);
            if (col > 1) {
                sheet.addMergedRegion(new CellRangeAddress(titleRow, titleRow, 0, col - 1));
            }
            if (r.subtitle() != null && !r.subtitle().isBlank()) {
                int subRow = rowIdx;
                Row sub = sheet.createRow(rowIdx++);
                sub.createCell(0).setCellValue(r.subtitle());
                if (col > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(subRow, subRow, 0, col - 1));
                }
            }
            rowIdx++;

            Row header = sheet.createRow(rowIdx++);
            for (int c = 0; c < col; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(r.headers().get(c));
                cell.setCellStyle(headStyle);
            }

            for (List<Object> data : r.rows()) {
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < data.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object v = data.get(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                        cell.setCellStyle(numStyle);
                    } else {
                        cell.setCellValue(v == null ? "" : v.toString());
                    }
                }
            }
            for (int c = 0; c < col; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("xlsx export failed", e);
        }
    }

    // ---------------------------------------------------------------- PDF

    public byte[] pdf(TabularReport r, String centerName) {
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 42, 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font brand = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_GREEN);
            Font h1 = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font small = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
            String name = centerName == null ? "SASI MILK SERVICE" : centerName;

            Image logo = logoImage();
            if (logo != null) {
                logo.scaleToFit(116, 48);
                PdfPTable head = new PdfPTable(new float[] {1f, 6f});
                head.setWidthPercentage(100);
                head.getDefaultCell().setBorder(Rectangle.NO_BORDER);

                PdfPCell logoCell = new PdfPCell(logo, false);
                logoCell.setBorder(Rectangle.NO_BORDER);
                logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                head.addCell(logoCell);

                PdfPCell textCell = new PdfPCell();
                textCell.setBorder(Rectangle.NO_BORDER);
                textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                textCell.addElement(new Paragraph(name, brand));
                textCell.addElement(new Paragraph(r.title(), h1));
                if (r.subtitle() != null && !r.subtitle().isBlank()) {
                    textCell.addElement(new Paragraph(r.subtitle(), small));
                }
                head.addCell(textCell);
                head.setSpacingAfter(10);
                doc.add(head);
            } else {
                Paragraph nameP = new Paragraph(name, brand);
                nameP.setSpacingAfter(2);
                doc.add(nameP);
                doc.add(new Paragraph(r.title(), h1));
                if (r.subtitle() != null && !r.subtitle().isBlank()) {
                    doc.add(new Paragraph(r.subtitle(), small));
                }
                doc.add(new Paragraph(" "));
            }

            PdfPTable table = new PdfPTable(r.headers().size());
            table.setWidthPercentage(100);

            Font headFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            for (String h : r.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(BRAND_GREEN);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font bodyFont = new Font(Font.HELVETICA, 9);
            boolean stripe = false;
            for (List<Object> row : r.rows()) {
                Color bg = stripe ? new Color(0xF3, 0xF6, 0xF0) : Color.WHITE;
                stripe = !stripe;
                for (Object v : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v.toString(), bodyFont));
                    cell.setBackgroundColor(bg);
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(v instanceof Number ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    table.addCell(cell);
                }
            }
            doc.add(table);

            Paragraph footer = new Paragraph(
                    "Generated " + LocalDateTime.now().format(STAMP) + "  ·  " + r.rows().size() + " rows", small);
            footer.setSpacingBefore(8);
            doc.add(footer);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("pdf export failed", e);
        }
    }

    private static Image logoImage() {
        if (LOGO == null) {
            return null;
        }
        try {
            return Image.getInstance(LOGO);
        } catch (Exception e) {
            log.warn("could not decode logo for pdf", e);
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    public static Object num(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static Object text(Object v) {
        return v == null ? "" : v;
    }

    public static Object date(LocalDate d) {
        return d == null ? "" : d.toString();
    }
}
