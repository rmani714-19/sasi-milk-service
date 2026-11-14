package com.sasimilkservice.report.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Producer milk statement as an A4 landscape PDF: every collection, totals, deductions, advances and payments. */
@Component
public class StatementPdfRenderer {

    private static final Color GREEN = new Color(0x2E, 0x7D, 0x5B);
    private static final Color INK = new Color(0x1B, 0x24, 0x1E);
    private static final Color MUTED = new Color(0x5C, 0x66, 0x5E);
    private static final Color LINE = new Color(0xD9, 0xDF, 0xDA);
    private static final Color WASH = new Color(0xF0, 0xF4, 0xF0);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy (EEE)", Locale.ENGLISH);
    private static final byte[] LOGO = loadLogo();

    private static byte[] loadLogo() {
        try (InputStream in = StatementPdfRenderer.class.getClassLoader().getResourceAsStream("report-logo.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] render(ReportService.CenterLine center, ReportService.DetailedStatement s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 32, 32, 30, 36);
        try {
            PdfWriter w = PdfWriter.getInstance(doc, out);
            w.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document d) {
                    PdfPTable f = new PdfPTable(1);
                    f.setTotalWidth(d.getPageSize().getWidth() - 64);
                    PdfPCell c = new PdfPCell(new Phrase("Page " + writer.getPageNumber()
                            + "  ·  This statement is generated from recorded milk collections and payment batches.",
                            font(7.5f, Font.NORMAL, MUTED)));
                    c.setBorder(Rectangle.NO_BORDER);
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    f.addCell(c);
                    f.writeSelectedRows(0, -1, 32, 26, writer.getDirectContent());
                }
            });
            doc.open();
            write(doc, center, s);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Unable to render the statement PDF", e);
        }
        return out.toByteArray();
    }

    private void write(Document doc, ReportService.CenterLine center, ReportService.DetailedStatement s)
            throws DocumentException {
        PdfPTable head = new PdfPTable(new float[]{0.7f, 5f, 3f});
        head.setWidthPercentage(100);
        PdfPCell logo = plain();
        if (LOGO != null) {
            try {
                Image img = Image.getInstance(LOGO);
                img.scaleToFit(46, 46);
                logo.addElement(img);
            } catch (Exception ignored) {
                // letterhead without a logo is fine
            }
        }
        head.addCell(logo);
        PdfPCell biz = plain();
        biz.addElement(new Paragraph(center.name(), font(15, Font.BOLD, GREEN)));
        if (notBlank(center.address())) {
            biz.addElement(new Paragraph(center.address(), font(8.5f, Font.NORMAL, MUTED)));
        }
        if (notBlank(center.mobile())) {
            biz.addElement(new Paragraph("Phone: " + center.mobile(), font(8.5f, Font.NORMAL, MUTED)));
        }
        head.addCell(biz);
        PdfPCell title = plain();
        title.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph t = new Paragraph("MILK COLLECTION STATEMENT", font(13, Font.BOLD, INK));
        t.setAlignment(Element.ALIGN_RIGHT);
        title.addElement(t);
        Paragraph per = new Paragraph(DATE.format(s.from()) + " to " + DATE.format(s.to()), font(10, Font.NORMAL, INK));
        per.setAlignment(Element.ALIGN_RIGHT);
        title.addElement(per);
        Paragraph gen = new Paragraph("Generated " + DATE.format(LocalDate.now()), font(8, Font.NORMAL, MUTED));
        gen.setAlignment(Element.ALIGN_RIGHT);
        title.addElement(gen);
        head.addCell(title);
        doc.add(head);

        // producer block
        PdfPTable who = new PdfPTable(new float[]{1.2f, 1.2f, 1.2f, 1.6f});
        who.setWidthPercentage(100);
        who.setSpacingBefore(8);
        who.addCell(box("PRODUCER", s.producerName()));
        who.addCell(box("PRODUCER ID", s.producerNumber()));
        who.addCell(box("REGION / AREA", notBlank(s.region()) ? s.region() : "-"));
        who.addCell(box("CONTACT", (notBlank(s.mobile()) ? s.mobile() : "-")
                + (notBlank(s.address()) ? "  ·  " + s.address() : "")));
        doc.add(who);

        // summary tiles
        PdfPTable tiles = new PdfPTable(6);
        tiles.setWidthPercentage(100);
        tiles.setSpacingBefore(8);
        tiles.addCell(tile("Total litres", num(s.totalLitres(), 2) + " L"));
        tiles.addCell(tile("Morning / Evening", num(s.morningLitres(), 2) + " / " + num(s.eveningLitres(), 2)));
        tiles.addCell(tile("Collection days", String.valueOf(s.collectionDays())));
        tiles.addCell(tile("Average rate / L", "Rs. " + num(s.averageRate(), 2)));
        tiles.addCell(tile("Milk amount", "Rs. " + num(s.milkAmount(), 2)));
        tiles.addCell(tile("Incentive", "Rs. " + num(s.incentive(), 2)));
        doc.add(tiles);

        // collections
        Paragraph ch = new Paragraph("Collection details", font(10.5f, Font.BOLD, INK));
        ch.setSpacingBefore(10);
        ch.setSpacingAfter(4);
        doc.add(ch);

        PdfPTable t1 = new PdfPTable(new float[]{1.55f, 0.8f, 0.65f, 2.0f, 0.85f, 0.65f, 0.65f, 0.85f, 1.1f, 1.0f, 1.1f});
        t1.setWidthPercentage(100);
        t1.setHeaderRows(1);
        String[] hdr = {"Date", "Shift", "Time", "Collection no.", "Litres", "FAT", "SNF", "Rate / L",
                "Milk amount", "Incentive", "Total"};
        for (int i = 0; i < hdr.length; i++) {
            PdfPCell hc = new PdfPCell(new Phrase(hdr[i], font(8, Font.BOLD, Color.WHITE)));
            hc.setBackgroundColor(GREEN);
            hc.setBorder(Rectangle.NO_BORDER);
            hc.setPadding(4);
            hc.setHorizontalAlignment(i < 4 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t1.addCell(hc);
        }
        if (s.collections().isEmpty()) {
            PdfPCell none = new PdfPCell(new Phrase("No collections were recorded in this period.",
                    font(9, Font.ITALIC, MUTED)));
            none.setColspan(hdr.length);
            none.setPadding(10);
            none.setBorder(Rectangle.NO_BORDER);
            t1.addCell(none);
        }
        boolean zebra = false;
        LocalDate lastDate = null;
        for (ReportService.StatementCollection c : s.collections()) {
            Color bg = zebra ? WASH : Color.WHITE;
            zebra = !zebra;
            boolean newDay = !c.date().equals(lastDate);
            lastDate = c.date();
            cell(t1, newDay ? DAY.format(c.date()) : "", Element.ALIGN_LEFT, bg);
            cell(t1, "MORNING".equals(c.shift()) ? "Morning" : "Evening", Element.ALIGN_LEFT, bg);
            cell(t1, c.recordedAt() == null ? "-" : c.recordedAt(), Element.ALIGN_LEFT, bg);
            cell(t1, c.collectionNumber(), Element.ALIGN_LEFT, bg);
            cell(t1, num(c.litres(), 2), Element.ALIGN_RIGHT, bg);
            cell(t1, c.fat() == null ? "-" : num(c.fat(), 1), Element.ALIGN_RIGHT, bg);
            cell(t1, c.snf() == null ? "-" : num(c.snf(), 1), Element.ALIGN_RIGHT, bg);
            cell(t1, rate(c.rate()), Element.ALIGN_RIGHT, bg);
            cell(t1, num(c.milkAmount(), 2), Element.ALIGN_RIGHT, bg);
            cell(t1, num(c.incentive(), 2), Element.ALIGN_RIGHT, bg);
            cell(t1, num(c.total(), 2), Element.ALIGN_RIGHT, bg);
        }
        PdfPCell tl = new PdfPCell(new Phrase("Total", font(9, Font.BOLD, INK)));
        tl.setColspan(4);
        tl.setBorder(Rectangle.TOP);
        tl.setBorderColor(INK);
        tl.setPadding(4);
        t1.addCell(tl);
        total(t1, num(s.totalLitres(), 2));
        total(t1, "");
        total(t1, "");
        total(t1, num(s.averageRate(), 2));
        total(t1, num(s.milkAmount(), 2));
        total(t1, num(s.incentive(), 2));
        total(t1, num(s.gross(), 2));
        doc.add(t1);

        // money summary + deductions
        PdfPTable sum = new PdfPTable(new float[]{3f, 1.5f});
        sum.setWidthPercentage(46);
        sum.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sum.setSpacingBefore(12);
        sum.setKeepTogether(true);
        sumRow(sum, "Milk amount", money(s.milkAmount()), false);
        sumRow(sum, "Incentive", "+ " + money(s.incentive()), false);
        sumRow(sum, "GROSS EARNINGS", money(s.gross()), true);
        for (ReportService.StatementCharge d : s.deductions()) {
            sumRow(sum, d.name() + " deduction", "- " + money(d.amount()), false);
        }
        if (s.deductions().isEmpty()) {
            sumRow(sum, "Deductions", "- " + money(BigDecimal.ZERO), false);
        }
        sumRow(sum, "Advance taken in period", money(s.advanceTaken()), false);
        sumRow(sum, "Advance recovered in period", "- " + money(s.advanceRecovered()), false);
        sumRow(sum, "Advance outstanding (today)", money(s.advanceOutstanding()), false);
        doc.add(sum);

        // payments
        if (!s.payments().isEmpty()) {
            Paragraph ph = new Paragraph("Payments for this period", font(10.5f, Font.BOLD, INK));
            ph.setSpacingBefore(10);
            ph.setSpacingAfter(4);
            doc.add(ph);
            PdfPTable pt = new PdfPTable(new float[]{1.5f, 2.3f, 1.6f, 1.1f, 1f, 1.1f, 1.2f, 1.3f, 1.4f});
            pt.setWidthPercentage(100);
            pt.setHeaderRows(1);
            String[] ph2 = {"Batch", "Period", "Bill no.", "Milk", "Incentive", "Deductions", "Net payable", "Status", "Bank ref"};
            for (int i = 0; i < ph2.length; i++) {
                PdfPCell hc = new PdfPCell(new Phrase(ph2[i], font(8, Font.BOLD, Color.WHITE)));
                hc.setBackgroundColor(GREEN);
                hc.setBorder(Rectangle.NO_BORDER);
                hc.setPadding(4);
                hc.setHorizontalAlignment(i >= 3 && i <= 6 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                pt.addCell(hc);
            }
            boolean z = false;
            for (ReportService.StatementPayment p : s.payments()) {
                Color bg = z ? WASH : Color.WHITE;
                z = !z;
                cell(pt, p.batch(), Element.ALIGN_LEFT, bg);
                cell(pt, DATE.format(p.periodStart()) + " - " + DATE.format(p.periodEnd()), Element.ALIGN_LEFT, bg);
                cell(pt, p.billNumber() == null ? "-" : p.billNumber(), Element.ALIGN_LEFT, bg);
                cell(pt, num(p.milk(), 2), Element.ALIGN_RIGHT, bg);
                cell(pt, num(p.incentive(), 2), Element.ALIGN_RIGHT, bg);
                cell(pt, num(p.deductions(), 2), Element.ALIGN_RIGHT, bg);
                cell(pt, num(p.netPayable(), 2), Element.ALIGN_RIGHT, bg);
                cell(pt, friendly(p.status()), Element.ALIGN_LEFT, bg);
                cell(pt, p.reference() == null ? "-" : p.reference(), Element.ALIGN_LEFT, bg);
            }
            doc.add(pt);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String friendly(String st) {
        return switch (st) {
            case "PENDING" -> "Pending";
            case "INITIATED", "PROCESSING" -> "Submitted to bank";
            case "SUCCESS" -> "Paid";
            case "FAILED" -> "Failed";
            case "ON_HOLD" -> "On hold";
            case "CANCELLED" -> "Cancelled";
            default -> st;
        };
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static PdfPCell plain() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(2);
        return c;
    }

    private static PdfPCell box(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINE);
        c.setPadding(6);
        c.addElement(new Paragraph(label, font(7, Font.BOLD, MUTED)));
        c.addElement(new Paragraph(value == null ? "" : value, font(9.5f, Font.BOLD, INK)));
        return c;
    }

    private static PdfPCell tile(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINE);
        c.setBackgroundColor(WASH);
        c.setPadding(6);
        c.addElement(new Paragraph(label, font(7.5f, Font.NORMAL, MUTED)));
        c.addElement(new Paragraph(value, font(11, Font.BOLD, INK)));
        return c;
    }

    private static void cell(PdfPTable t, String text, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font(8.5f, Font.NORMAL, INK)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(bg);
        c.setPadding(3.5f);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private static void total(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(9, Font.BOLD, INK)));
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(INK);
        c.setPadding(4);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private static void sumRow(PdfPTable t, String label, String value, boolean strong) {
        Font f = strong ? font(10, Font.BOLD, Color.WHITE) : font(9, Font.NORMAL, INK);
        PdfPCell l = new PdfPCell(new Phrase(label, f));
        PdfPCell v = new PdfPCell(new Phrase(value, f));
        for (PdfPCell c : List.of(l, v)) {
            c.setPadding(4.5f);
            if (strong) {
                c.setBackgroundColor(GREEN);
                c.setBorder(Rectangle.NO_BORDER);
            } else {
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderColor(LINE);
            }
        }
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String num(BigDecimal v, int scale) {
        return String.format(Locale.ENGLISH, "%,." + scale + "f",
                (v == null ? BigDecimal.ZERO : v).setScale(scale, RoundingMode.HALF_UP));
    }

    private static String money(BigDecimal v) {
        return "Rs. " + num(v, 2);
    }

    private static String rate(BigDecimal v) {
        if (v == null) {
            return "-";
        }
        BigDecimal r = v.stripTrailingZeros();
        if (r.scale() < 2) {
            r = r.setScale(2, RoundingMode.UNNECESSARY);
        }
        return r.toPlainString();
    }
}
