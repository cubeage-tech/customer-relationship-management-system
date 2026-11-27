package com.company.crm.analytics.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FR-9.7: renders a {@link ReportDocument} as a landscape A4 PDF — title, period, generation
 * time, then each table with a bold header row, breaking onto new pages as needed. Built with
 * PDFBox, like the quotation PDF. Stateless: each call carries its own {@link Cursor}.
 */
@Component
public class ReportPdfWriter {

    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 36;
    private static final float ROW = 14;
    private static final float FONT_SIZE = 8;
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final class Cursor {
        final PDDocument document;
        PDPageContentStream content;
        float y;

        Cursor(PDDocument document) {
            this.document = document;
        }
    }

    public byte[] write(ReportDocument doc) {
        try (PDDocument document = new PDDocument()) {
            Cursor c = new Cursor(document);
            newPage(c);
            text(c, bold, 16, MARGIN, doc.title());
            c.y -= 18;
            text(c, regular, 10, MARGIN, doc.subtitle());
            c.y -= 13;
            text(c, regular, 9, MARGIN, "Generated " + GENERATED.format(doc.generatedAt()));
            c.y -= 22;
            for (ReportDocument.Table table : doc.tables()) {
                writeTable(c, table);
            }
            c.content.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeTable(Cursor c, ReportDocument.Table table) throws IOException {
        if (c.y < MARGIN + ROW * 4) {
            newPage(c);
        }
        text(c, bold, 11, MARGIN, table.title());
        c.y -= ROW + 2;
        float width = (PAGE.getWidth() - 2 * MARGIN) / Math.max(1, table.headers().size());
        header(c, table, width);
        if (table.rows().isEmpty()) {
            text(c, regular, FONT_SIZE, MARGIN, "Nothing in this period.");
            c.y -= ROW;
        }
        for (List<Object> row : table.rows()) {
            if (c.y < MARGIN + ROW) {
                newPage(c);
                header(c, table, width);
            }
            for (int i = 0; i < row.size(); i++) {
                text(c, regular, FONT_SIZE, MARGIN + i * width, fit(regular, format(row.get(i)), width - 4));
            }
            c.y -= ROW;
        }
        c.y -= ROW;
    }

    private void header(Cursor c, ReportDocument.Table table, float width) throws IOException {
        c.content.setNonStrokingColor(new Color(241, 245, 249));
        c.content.addRect(MARGIN - 2, c.y - 4, PAGE.getWidth() - 2 * MARGIN + 4, ROW);
        c.content.fill();
        c.content.setNonStrokingColor(Color.BLACK);
        for (int i = 0; i < table.headers().size(); i++) {
            text(c, bold, FONT_SIZE, MARGIN + i * width, fit(bold, table.headers().get(i), width - 4));
        }
        c.y -= ROW;
    }

    private void newPage(Cursor c) throws IOException {
        if (c.content != null) {
            c.content.close();
        }
        PDPage page = new PDPage(PAGE);
        c.document.addPage(page);
        c.content = new PDPageContentStream(c.document, page);
        c.y = PAGE.getHeight() - MARGIN;
    }

    private void text(Cursor c, PDFont font, float size, float x, String value) throws IOException {
        c.content.beginText();
        c.content.setFont(font, size);
        c.content.newLineAtOffset(x, c.y);
        c.content.showText(safe(font, value));
        c.content.endText();
    }

    private static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal || v instanceof Double) {
            return new DecimalFormat("#,##0.##").format(v);
        }
        return v.toString();
    }

    /** Truncates with an ellipsis so a long value can't spill into the next column. */
    private static String fit(PDFont font, String value, float width) throws IOException {
        String s = safe(font, value);
        if (font.getStringWidth(s) / 1000 * FONT_SIZE <= width) {
            return s;
        }
        while (!s.isEmpty() && font.getStringWidth(s + "...") / 1000 * FONT_SIZE > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    /** Replaces characters the standard PDF font can't draw (e.g. ₹, emoji), as QuotationPdf does. */
    private static String safe(PDFont font, String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                out.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                out.append('?');
            }
        });
        return out.toString();
    }
}
