package com.sasimilkservice.driversalary.statement;

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

/** Driver monthly salary statements as A4 portrait PDFs — one driver per section, several per document. */
@Component
public class SalaryStatementPdfRenderer {

    private static final Color GREEN = new Color(0x2E, 0x7D, 0x5B);
    private static final Color INK = new Color(0x1B, 0x24, 0x1E);
    private static final Color MUTED = new Color(0x5C, 0x66, 0x5E);
    private static final Color LINE = new Color(0xD9, 0xDF, 0xDA);
    private static final Color WASH = new Color(0xF0, 0xF4, 0xF0);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final byte[] LOGO = loadLogo();

    private static byte[] loadLogo() {
        try (InputStream in = SalaryStatementPdfRenderer.class.getClassLoader().getResourceAsStream("report-logo.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] render(List<SalaryStatementData> statements) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 34, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            boolean first = true;
            for (SalaryStatementData s : statements) {
                if (!first) {
                    doc.newPage();
                }
                first = false;
                write(doc, s);
            }
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Unable to render the salary statement PDF", e);
        }
        return out.toByteArray();
    }

    private void write(Document doc, SalaryStatementData s) throws DocumentException {
        PdfPTable head = new PdfPTable(new float[]{1f, 4f});
        head.setWidthPercentage(100);
        PdfPCell logo = plain();
        if (LOGO != null) {
            try {
                Image img = Image.getInstance(LOGO);
                img.scaleToFit(52, 52);
                logo.addElement(img);
            } catch (Exception ignored) {
                // no logo is fine
            }
        }
        head.addCell(logo);
        PdfPCell biz = plain();
        biz.addElement(new Paragraph(s.centerName(), font(15, Font.BOLD, GREEN)));
        if (notBlank(s.centerAddress())) {
            biz.addElement(new Paragraph(s.centerAddress(), font(9, Font.NORMAL, MUTED)));
        }
        if (notBlank(s.centerMobile())) {
            biz.addElement(new Paragraph("Phone: " + s.centerMobile(), font(9, Font.NORMAL, MUTED)));
        }
        head.addCell(biz);
        doc.add(head);

        Paragraph title = new Paragraph("Driver Salary Statement - " + MONTH.format(s.month()), font(14, Font.BOLD, INK));
        title.setSpacingBefore(10);
        title.setSpacingAfter(8);
        doc.add(title);

        PdfPTable who = new PdfPTable(new float[]{1f, 1f, 1f});
        who.setWidthPercentage(100);
        who.addCell(box("DRIVER", s.driverName() + "  (" + s.driverCode() + ")"));
        who.addCell(box("VEHICLE", s.vehicle() == null ? "Not assigned" : s.vehicle()));
        who.addCell(box("SALARY BATCH", s.batchNumber()));
        doc.add(who);

        // ---- salary
        section(doc, "Salary");
        PdfPTable sal = new PdfPTable(new float[]{3f, 1.6f, 3f, 1.6f});
        sal.setWidthPercentage(100);
        String basicLabel = "PER_DAY".equals(s.salaryType())
                ? "Daily rate x " + trim(s.workingDays()) + " days" : "Basic salary";
        BigDecimal basicAmount = "PER_DAY".equals(s.salaryType()) ? s.basicSalary().multiply(s.workingDays()) : s.basicSalary();
        line(sal, basicLabel, money(basicAmount), "Salary advance", money(s.salaryAdvance()));
        line(sal, "Allowances", money(s.allowances()), "Loan recovery", money(s.loanRecovery()));
        line(sal, "Overtime", money(s.overtime()), "Penalty", money(s.penalty()));
        line(sal, "Other earnings", money(s.otherEarnings()), "Other deductions", money(s.otherDeduction()));
        totalLine(sal, "GROSS EARNINGS", money(s.gross()), "TOTAL DEDUCTIONS", money(s.totalDeduction()));
        doc.add(sal);

        PdfPTable net = new PdfPTable(new float[]{3f, 1.6f});
        net.setWidthPercentage(60);
        net.setHorizontalAlignment(Element.ALIGN_RIGHT);
        net.setSpacingBefore(6);
        PdfPCell nl = new PdfPCell(new Phrase("NET SALARY", font(12, Font.BOLD, Color.WHITE)));
        PdfPCell nv = new PdfPCell(new Phrase(money(s.net()), font(12, Font.BOLD, Color.WHITE)));
        for (PdfPCell c : List.of(nl, nv)) {
            c.setBackgroundColor(GREEN);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(6);
        }
        nv.setHorizontalAlignment(Element.ALIGN_RIGHT);
        net.addCell(nl);
        net.addCell(nv);
        doc.add(net);
        Paragraph st = new Paragraph("Payment status: " + friendly(s.paymentStatus())
                + (s.paymentMethod() == null ? "" : "  ·  " + s.paymentMethod().replace('_', ' ').toLowerCase())
                + (notBlank(s.paymentReference()) ? "  ·  Ref " + s.paymentReference() : ""),
                font(9, Font.NORMAL, INK));
        st.setSpacingBefore(4);
        doc.add(st);

        // ---- route
        section(doc, "Route collection - " + (s.routeRegions().isEmpty() ? "no route assigned"
                : String.join(", ", s.routeRegions())));
        if (s.routeLines().isEmpty()) {
            doc.add(new Paragraph(s.routeRegions().isEmpty()
                    ? "Assign regions to this driver's route to see the litres collected on it."
                    : "No milk was collected on this route in " + MONTH.format(s.month()) + ".",
                    font(9, Font.ITALIC, MUTED)));
        } else {
            PdfPTable rt = new PdfPTable(new float[]{1.6f, 2f, 1.2f, 1.2f, 1.2f});
            rt.setWidthPercentage(100);
            rt.setHeaderRows(1);
            header(rt, List.of("Date", "Region / area", "Morning (L)", "Evening (L)", "Total (L)"), 2);
            boolean z = false;
            BigDecimal m = BigDecimal.ZERO;
            BigDecimal e = BigDecimal.ZERO;
            for (SalaryStatementData.RouteLine r : s.routeLines()) {
                Color bg = z ? WASH : Color.WHITE;
                z = !z;
                body(rt, DATE.format(r.date()), Element.ALIGN_LEFT, bg);
                body(rt, r.region(), Element.ALIGN_LEFT, bg);
                body(rt, num(r.morning()), Element.ALIGN_RIGHT, bg);
                body(rt, num(r.evening()), Element.ALIGN_RIGHT, bg);
                body(rt, num(r.total()), Element.ALIGN_RIGHT, bg);
                m = m.add(r.morning());
                e = e.add(r.evening());
            }
            totalCell(rt, "Total route litres", 2, Element.ALIGN_LEFT);
            totalCell(rt, num(m), 1, Element.ALIGN_RIGHT);
            totalCell(rt, num(e), 1, Element.ALIGN_RIGHT);
            totalCell(rt, num(m.add(e)), 1, Element.ALIGN_RIGHT);
            doc.add(rt);
        }

        // ---- vehicle expenses
        section(doc, "Vehicle expenses" + (s.vehicle() == null ? "" : " - " + s.vehicle()));
        if (s.expenses().isEmpty()) {
            doc.add(new Paragraph("No expenses recorded for the vehicle this month.", font(9, Font.ITALIC, MUTED)));
        } else {
            PdfPTable et = new PdfPTable(new float[]{1.6f, 1.6f, 4f, 1.4f});
            et.setWidthPercentage(100);
            et.setHeaderRows(1);
            header(et, List.of("Date", "Category", "Description", "Amount"), 3);
            boolean z = false;
            for (SalaryStatementData.ExpenseLine x : s.expenses()) {
                Color bg = z ? WASH : Color.WHITE;
                z = !z;
                body(et, DATE.format(x.date()), Element.ALIGN_LEFT, bg);
                body(et, pretty(x.category()), Element.ALIGN_LEFT, bg);
                body(et, x.description() == null ? "-" : x.description(), Element.ALIGN_LEFT, bg);
                body(et, num(x.amount()), Element.ALIGN_RIGHT, bg);
            }
            totalCell(et, "Total vehicle expenses (shown for information - not deducted from salary)", 3, Element.ALIGN_LEFT);
            totalCell(et, num(s.expenseTotal()), 1, Element.ALIGN_RIGHT);
            doc.add(et);
        }

        // ---- advances
        section(doc, "Advances");
        if (s.advances().isEmpty()) {
            doc.add(new Paragraph("No advances to deduct.", font(9, Font.ITALIC, MUTED)));
        } else {
            PdfPTable at = new PdfPTable(new float[]{1.6f, 4f, 1.4f, 2f});
            at.setWidthPercentage(100);
            at.setHeaderRows(1);
            header(at, List.of("Date given", "Reason", "Amount", "Status"), 2);
            boolean z = false;
            for (SalaryStatementData.AdvanceLine a : s.advances()) {
                Color bg = z ? WASH : Color.WHITE;
                z = !z;
                body(at, DATE.format(a.date()), Element.ALIGN_LEFT, bg);
                body(at, a.reason() == null ? "-" : a.reason(), Element.ALIGN_LEFT, bg);
                body(at, num(a.amount()), Element.ALIGN_RIGHT, bg);
                body(at, a.status(), Element.ALIGN_LEFT, bg);
            }
            doc.add(at);
        }

        // ---- sign + footer
        PdfPTable sign = new PdfPTable(2);
        sign.setWidthPercentage(100);
        sign.setSpacingBefore(40);
        for (String label : List.of("Driver signature", "Authorized signature")) {
            PdfPCell c = new PdfPCell(new Phrase(label, font(9, Font.NORMAL, MUTED)));
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(MUTED);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPaddingTop(4);
            PdfPCell gap = plain();
            sign.addCell(c);
        }
        doc.add(sign);
        Paragraph foot = new Paragraph("Generated " + DATE.format(LocalDate.now())
                + ". Payment is made through the bank or in cash and recorded here once confirmed.",
                font(8, Font.ITALIC, MUTED));
        foot.setSpacingBefore(14);
        foot.setAlignment(Element.ALIGN_CENTER);
        doc.add(foot);
    }

    // ---------------------------------------------------------------- helpers

    private static void section(Document doc, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, font(10.5f, Font.BOLD, INK));
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        doc.add(p);
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
        c.setPadding(7);
        c.addElement(new Paragraph(label, font(7.5f, Font.BOLD, MUTED)));
        c.addElement(new Paragraph(value == null ? "" : value, font(10, Font.BOLD, INK)));
        return c;
    }

    private static void line(PdfPTable t, String l1, String v1, String l2, String v2) {
        cellLine(t, l1, Element.ALIGN_LEFT, false);
        cellLine(t, v1, Element.ALIGN_RIGHT, false);
        cellLine(t, l2, Element.ALIGN_LEFT, false);
        cellLine(t, v2, Element.ALIGN_RIGHT, false);
    }

    private static void totalLine(PdfPTable t, String l1, String v1, String l2, String v2) {
        cellLine(t, l1, Element.ALIGN_LEFT, true);
        cellLine(t, v1, Element.ALIGN_RIGHT, true);
        cellLine(t, l2, Element.ALIGN_LEFT, true);
        cellLine(t, v2, Element.ALIGN_RIGHT, true);
    }

    private static void cellLine(PdfPTable t, String text, int align, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(9, bold ? Font.BOLD : Font.NORMAL, INK)));
        c.setBorder(bold ? Rectangle.TOP : Rectangle.BOTTOM);
        c.setBorderColor(bold ? INK : LINE);
        c.setPadding(4.5f);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private static void header(PdfPTable t, List<String> cols, int leftCols) {
        for (int i = 0; i < cols.size(); i++) {
            PdfPCell hc = new PdfPCell(new Phrase(cols.get(i), font(8, Font.BOLD, Color.WHITE)));
            hc.setBackgroundColor(GREEN);
            hc.setBorder(Rectangle.NO_BORDER);
            hc.setPadding(4.5f);
            hc.setHorizontalAlignment(i < leftCols ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(hc);
        }
    }

    private static void body(PdfPTable t, String text, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8.5f, Font.NORMAL, INK)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(bg);
        c.setPadding(3.5f);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private static void totalCell(PdfPTable t, String text, int colspan, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8.5f, Font.BOLD, INK)));
        c.setColspan(colspan);
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(INK);
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private static String friendly(String st) {
        return switch (st) {
            case "PAID" -> "Paid";
            case "PENDING" -> "Pending";
            case "PROCESSING" -> "Submitted to bank";
            case "FAILED" -> "Failed";
            case "ON_HOLD" -> "On hold";
            default -> st;
        };
    }

    private static String pretty(String s) {
        String t = s.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String num(BigDecimal v) {
        return String.format(Locale.ENGLISH, "%,.2f", (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP));
    }

    private static String money(BigDecimal v) {
        return "Rs. " + num(v);
    }
}
