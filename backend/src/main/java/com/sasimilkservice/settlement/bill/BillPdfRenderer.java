package com.sasimilkservice.settlement.bill;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Renders producer payment bills as professional A4 PDFs. One bill per page; several bills can share
 * one document for "download all". Money is formatted for reading only — every figure comes from the
 * stored settlement line, nothing is recalculated here.
 */
@Component
public class BillPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(BillPdfRenderer.class);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x5B);
    private static final Color INK = new Color(0x1B, 0x24, 0x1E);
    private static final Color MUTED = new Color(0x5C, 0x66, 0x5E);
    private static final Color LINE = new Color(0xD9, 0xDF, 0xDA);
    private static final Color WASH = new Color(0xF0, 0xF4, 0xF0);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static final byte[] LOGO = loadLogo();

    private static byte[] loadLogo() {
        try (InputStream in = BillPdfRenderer.class.getClassLoader().getResourceAsStream("report-logo.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            log.warn("report-logo.png could not be read; bills will have no logo", e);
            return null;
        }
    }

    public byte[] render(List<BillData> bills) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            boolean first = true;
            for (BillData b : bills) {
                if (!first) {
                    doc.newPage();
                }
                first = false;
                writeBill(doc, b);
            }
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Unable to render the bill PDF", e);
        }
        return out.toByteArray();
    }

    private void writeBill(Document doc, BillData b) throws DocumentException {
        // ---- letterhead
        PdfPTable head = new PdfPTable(new float[]{1f, 4f});
        head.setWidthPercentage(100);
        PdfPCell logo = cell(Rectangle.NO_BORDER);
        if (LOGO != null) {
            try {
                Image img = Image.getInstance(LOGO);
                img.scaleToFit(56, 56);
                logo.addElement(img);
            } catch (Exception e) {
                log.warn("could not embed bill logo", e);
            }
        }
        head.addCell(logo);
        PdfPCell biz = cell(Rectangle.NO_BORDER);
        biz.addElement(new Paragraph(b.centerName(), font(16, Font.BOLD, GREEN)));
        if (notBlank(b.centerAddress())) {
            biz.addElement(new Paragraph(b.centerAddress(), font(9, Font.NORMAL, MUTED)));
        }
        if (notBlank(b.centerMobile())) {
            biz.addElement(new Paragraph("Phone: " + b.centerMobile(), font(9, Font.NORMAL, MUTED)));
        }
        head.addCell(biz);
        doc.add(head);

        Paragraph title = new Paragraph(b.title(), font(14, Font.BOLD, INK));
        title.setSpacingBefore(10);
        title.setSpacingAfter(8);
        doc.add(title);

        // ---- bill facts
        PdfPTable facts = new PdfPTable(new float[]{1.1f, 1.6f, 1.1f, 1.6f});
        facts.setWidthPercentage(100);
        fact(facts, "Bill number", b.billNumber() == null ? "Not generated yet" : b.billNumber());
        fact(facts, "Payment batch", b.batchNumber());
        fact(facts, "Payment period", DATE.format(b.periodStart()) + " to " + DATE.format(b.periodEnd()));
        fact(facts, "Bill date", DATE.format(b.generatedOn()));
        doc.add(facts);

        // ---- producer + bank
        PdfPTable parties = new PdfPTable(new float[]{1f, 1f});
        parties.setWidthPercentage(100);
        parties.setSpacingBefore(10);
        PdfPCell prod = boxed();
        prod.addElement(new Paragraph("PRODUCER", font(8, Font.BOLD, MUTED)));
        prod.addElement(new Paragraph(b.producerName(), font(11, Font.BOLD, INK)));
        prod.addElement(new Paragraph("Producer ID: " + b.producerNumber(), font(9, Font.NORMAL, INK)));
        if (notBlank(b.region())) {
            prod.addElement(new Paragraph("Region: " + b.region(), font(9, Font.NORMAL, INK)));
        }
        parties.addCell(prod);
        PdfPCell bank = boxed();
        bank.addElement(new Paragraph("BANK ACCOUNT", font(8, Font.BOLD, MUTED)));
        if (notBlank(b.accountMasked())) {
            bank.addElement(new Paragraph(nz(b.accountHolder()), font(11, Font.BOLD, INK)));
            bank.addElement(new Paragraph("Account: " + b.accountMasked(), font(9, Font.NORMAL, INK)));
            bank.addElement(new Paragraph(
                    (notBlank(b.bankName()) ? b.bankName() + " · " : "") + "IFSC " + nz(b.ifsc()),
                    font(9, Font.NORMAL, INK)));
        } else {
            bank.addElement(new Paragraph("No bank account on file", font(9, Font.NORMAL, MUTED)));
        }
        parties.addCell(bank);
        doc.add(parties);

        // ---- collections
        Paragraph ch = new Paragraph("Collection details", font(10, Font.BOLD, INK));
        ch.setSpacingBefore(12);
        ch.setSpacingAfter(4);
        doc.add(ch);

        PdfPTable t = new PdfPTable(new float[]{1.5f, 1.1f, 1f, 0.8f, 0.8f, 1.1f, 1.4f, 1.2f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : List.of("Date", "Shift", "Litres", "FAT", "SNF", "Rate / L", "Milk amount", "Incentive")) {
            PdfPCell hc = new PdfPCell(new Phrase(h, font(8, Font.BOLD, Color.WHITE)));
            hc.setBackgroundColor(GREEN);
            hc.setBorder(Rectangle.NO_BORDER);
            hc.setPadding(5);
            hc.setHorizontalAlignment(h.equals("Date") || h.equals("Shift") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(hc);
        }
        boolean zebra = false;
        for (BillData.Line l : b.lines()) {
            Color bg = zebra ? WASH : Color.WHITE;
            zebra = !zebra;
            row(t, DATE.format(l.date()), Element.ALIGN_LEFT, bg);
            row(t, "MORNING".equals(l.shift()) ? "Morning" : "Evening", Element.ALIGN_LEFT, bg);
            row(t, num(l.litres(), 2), Element.ALIGN_RIGHT, bg);
            row(t, l.fat() == null ? "-" : num(l.fat(), 1), Element.ALIGN_RIGHT, bg);
            row(t, l.snf() == null ? "-" : num(l.snf(), 1), Element.ALIGN_RIGHT, bg);
            row(t, rate(l.rate()), Element.ALIGN_RIGHT, bg);
            row(t, num(l.milkAmount(), 2), Element.ALIGN_RIGHT, bg);
            row(t, num(l.incentive(), 2), Element.ALIGN_RIGHT, bg);
        }
        PdfPCell tot = new PdfPCell(new Phrase("Total", font(9, Font.BOLD, INK)));
        tot.setColspan(2);
        tot.setBorder(Rectangle.TOP);
        tot.setBorderColor(INK);
        tot.setPadding(5);
        t.addCell(tot);
        total(t, num(b.totalLitres(), 2));
        total(t, "");
        total(t, "");
        total(t, "");
        total(t, num(b.milkAmount(), 2));
        total(t, num(b.incentive(), 2));
        doc.add(t);

        // ---- calculation summary
        PdfPTable sum = new PdfPTable(new float[]{3f, 1.6f});
        sum.setWidthPercentage(58);
        sum.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sum.setSpacingBefore(14);
        sumRow(sum, "Total milk quantity", num(b.totalLitres(), 2) + " L", false);
        sumRow(sum, "Total milk amount", money(b.milkAmount()), false);
        sumRow(sum, "Incentives", "+ " + money(b.incentive()), false);
        sumRow(sum, "Cattle feed deduction", "- " + money(b.feedDeduction()), false);
        sumRow(sum, "Veterinary deduction", "- " + money(b.veterinaryDeduction()), false);
        sumRow(sum, "Other deductions", "- " + money(b.otherDeduction()), false);
        sumRow(sum, "Advance recovery", "- " + money(b.advanceRecovery()), false);
        for (BillData.AdjustmentLine a : b.adjustmentLines()) {
            boolean credit = "CREDIT".equals(a.type());
            sumRow(sum, "Adjustment" + (notBlank(a.reason()) ? " (" + a.reason() + ")" : ""),
                    (credit ? "+ " : "- ") + money(a.amount()), false);
        }
        if (b.roundOff() != null && b.roundOff().signum() != 0) {
            sumRow(sum, "Rounding", (b.roundOff().signum() > 0 ? "+ " : "- ") + money(b.roundOff().abs()), false);
        }
        sumRow(sum, "NET PAYABLE", money(b.netPayable()), true);
        doc.add(sum);

        // ---- status
        Paragraph st = new Paragraph("Payment status: " + b.preparationStatus()
                + (notBlank(b.bankReference()) ? "   ·   Bank reference: " + b.bankReference() : ""),
                font(9, Font.NORMAL, INK));
        st.setSpacingBefore(12);
        doc.add(st);
        if (b.billNumber() == null) {
            Paragraph pv = new Paragraph(
                    "PREVIEW - the bill number is assigned when bills are generated from verified collections.",
                    font(8, Font.ITALIC, new Color(0xA5, 0x39, 0x3A)));
            pv.setSpacingBefore(4);
            doc.add(pv);
        }

        // ---- signature + footer
        PdfPTable sign = new PdfPTable(1);
        sign.setWidthPercentage(36);
        sign.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(46);
        PdfPCell sl = new PdfPCell(new Phrase("Authorized Signature", font(9, Font.NORMAL, MUTED)));
        sl.setBorder(Rectangle.TOP);
        sl.setBorderColor(MUTED);
        sl.setHorizontalAlignment(Element.ALIGN_CENTER);
        sl.setPaddingTop(4);
        sign.addCell(sl);
        doc.add(sign);

        Paragraph foot = new Paragraph(
                "This bill is generated for payment preparation and bank processing. "
                        + "Actual payment is subject to bank confirmation.",
                font(8, Font.ITALIC, MUTED));
        foot.setSpacingBefore(18);
        foot.setAlignment(Element.ALIGN_CENTER);
        doc.add(foot);
    }

    // ---------------------------------------------------------------- helpers

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static PdfPCell cell(int border) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        c.setPadding(2);
        return c;
    }

    private static PdfPCell boxed() {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINE);
        c.setPadding(8);
        return c;
    }

    private static void fact(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, font(8, Font.NORMAL, MUTED)));
        l.setBorder(Rectangle.BOTTOM);
        l.setBorderColor(LINE);
        l.setPadding(4);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(nz(value), font(9, Font.BOLD, INK)));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColor(LINE);
        v.setPadding(4);
        t.addCell(v);
    }

    private static void row(PdfPTable t, String text, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8.5f, Font.NORMAL, INK)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(bg);
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private static void total(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(9, Font.BOLD, INK)));
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(INK);
        c.setPadding(5);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private static void sumRow(PdfPTable t, String label, String value, boolean strong) {
        Font f = strong ? font(11, Font.BOLD, Color.WHITE) : font(9, Font.NORMAL, INK);
        PdfPCell l = new PdfPCell(new Phrase(label, f));
        PdfPCell v = new PdfPCell(new Phrase(value, f));
        for (PdfPCell c : List.of(l, v)) {
            c.setPadding(5);
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String num(BigDecimal v, int scale) {
        return String.format(Locale.ENGLISH, "%,." + scale + "f",
                (v == null ? BigDecimal.ZERO : v).setScale(scale, RoundingMode.HALF_UP));
    }

    private static String money(BigDecimal v) {
        return "Rs. " + num(v, 2);
    }

    /** Rate keeps its real precision (at least two decimals) so litres x rate is verifiable on paper. */
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
