package com.company.crm.quotation.pdf;

import com.company.crm.branding.service.BrandingService;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.entity.QuotationLineItem;
import com.company.crm.tenant.entity.Tenant;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * FR-4.4: generates a downloadable, professionally formatted PDF proposal from a
 * quotation — company name, line items, totals. Built directly with PDFBox rather
 * than an HTML/CSS template engine, to keep the dependency footprint small.
 *
 * Stateless and thread-safe: every render call carries its own {@link Cursor} rather
 * than storing position/stream on the bean, which is a singleton shared across requests.
 */
@Component
public class QuotationPdf {

    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final float LOGO_MAX_WIDTH = 140;
    private static final float LOGO_MAX_HEIGHT = 60;

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final BrandingService brandingService;

    public QuotationPdf(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    /** Per-call render position — a page/content-stream pair plus the current y offset. */
    private static final class Cursor {
        PDPageContentStream content;
        float y;

        Cursor(PDPageContentStream content, float y) {
            this.content = content;
            this.y = y;
        }
    }

    public byte[] generate(Quotation quotation) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            Cursor cursor = new Cursor(new PDPageContentStream(document, page), PAGE_HEIGHT - MARGIN);

            writeHeader(document, cursor, quotation);
            writeCustomer(cursor, quotation);
            writeLineItemsTable(document, cursor, quotation);
            writeTotals(cursor, quotation);

            cursor.content.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate quotation PDF", e);
        }
    }

    /**
     * AC-4.2: company branding — logo (top right), company name in the brand colour, then the
     * address, phone/email and tax number when set.
     */
    private void writeHeader(PDDocument document, Cursor cursor, Quotation quotation) throws IOException {
        Tenant tenant = quotation.getTenant();
        float top = cursor.y;
        float logoBottom = top;
        byte[] logo = brandingService.logoBytes(tenant);
        if (logo != null) {
            try {
                PDImageXObject image = PDImageXObject.createFromByteArray(document, logo, "logo");
                float scale = Math.min(LOGO_MAX_WIDTH / image.getWidth(), LOGO_MAX_HEIGHT / image.getHeight());
                float width = image.getWidth() * scale;
                float height = image.getHeight() * scale;
                cursor.content.drawImage(image, PAGE_WIDTH - MARGIN - width, top - height + 14, width, height);
                logoBottom = top - height + 14;
            } catch (IOException | IllegalArgumentException e) {
                // A broken logo must not stop the quotation from being generated.
            }
        }

        cursor.content.setNonStrokingColor(brandColor(tenant.getBrandColor()));
        text(cursor, bold, 20, MARGIN, safe(bold, tenant.getCompanyName()));
        cursor.content.setNonStrokingColor(Color.BLACK);
        cursor.y -= 16;
        List<String> details = new ArrayList<>();
        if (tenant.getBrandAddress() != null) {
            details.addAll(Arrays.asList(tenant.getBrandAddress().split("\\R")));
        }
        String contact = String.join("  |  ", Stream.of(tenant.getBrandPhone(), tenant.getBrandEmail()).filter(Objects::nonNull).toList());
        if (!contact.isEmpty()) {
            details.add(contact);
        }
        if (tenant.getBrandTaxId() != null) {
            details.add("GSTIN: " + tenant.getBrandTaxId());
        }
        cursor.content.setNonStrokingColor(Color.DARK_GRAY);
        for (String detail : details) {
            if (!detail.isBlank()) {
                text(cursor, regular, 9, MARGIN, safe(regular, detail.trim()));
                cursor.y -= 12;
            }
        }
        cursor.content.setNonStrokingColor(Color.BLACK);
        cursor.y = Math.min(cursor.y, logoBottom) - 16;

        text(cursor, bold, 14, MARGIN, "QUOTATION");
        cursor.y -= 20;
        text(cursor, regular, 10, MARGIN, "Quotation #: " + quotation.getQuotationNumber());
        cursor.y -= 14;
        text(cursor, regular, 10, MARGIN, "Date: " + quotation.getCreatedAt().toLocalDate().format(DATE_FORMAT));
        if (quotation.getValidUntil() != null) {
            cursor.y -= 14;
            text(cursor, regular, 10, MARGIN, "Valid until: " + quotation.getValidUntil().format(DATE_FORMAT));
        }
        cursor.y -= 28;
    }

    private void writeCustomer(Cursor cursor, Quotation quotation) throws IOException {
        text(cursor, bold, 11, MARGIN, "Bill To");
        cursor.y -= 16;
        text(cursor, regular, 10, MARGIN, quotation.getCustomer().getCompanyName());
        if (quotation.getCustomer().getEmail() != null) {
            cursor.y -= 14;
            text(cursor, regular, 10, MARGIN, quotation.getCustomer().getEmail());
        }
        if (quotation.getCustomer().getPhone() != null) {
            cursor.y -= 14;
            text(cursor, regular, 10, MARGIN, quotation.getCustomer().getPhone());
        }
        cursor.y -= 28;
    }

    private void writeLineItemsTable(PDDocument document, Cursor cursor, Quotation quotation) throws IOException {
        float[] columnX = {MARGIN, 260, 330, 410, 480};

        drawTableRow(cursor, bold, columnX, new String[]{"Product / Service", "Qty", "Unit Price", "Discount", "Line Total"});
        cursor.y -= 4;
        line(cursor);
        cursor.y -= 14;

        for (QuotationLineItem item : quotation.getLineItems()) {
            if (cursor.y < MARGIN + 120) {
                cursor.content.close();
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                cursor.content = new PDPageContentStream(document, page);
                cursor.y = PAGE_HEIGHT - MARGIN;
            }

            drawTableRow(cursor, regular, columnX, new String[]{
                    item.getProductName(),
                    item.getQuantity().stripTrailingZeros().toPlainString(),
                    formatCurrency(item.getUnitPrice()),
                    item.getDiscountPercent() + "%",
                    formatCurrency(item.getLineTotal())
            });
            cursor.y -= 4;
        }

        cursor.y -= 10;
    }

    private void writeTotals(Cursor cursor, Quotation quotation) throws IOException {
        line(cursor);
        cursor.y -= 20;

        BigDecimal discountTotal = quotation.getSubtotal().subtract(quotation.getGrandTotal());

        text(cursor, regular, 10, 380, "Subtotal:");
        text(cursor, regular, 10, 480, formatCurrency(quotation.getSubtotal()));
        cursor.y -= 16;
        text(cursor, regular, 10, 380, "Discount:");
        text(cursor, regular, 10, 480, formatCurrency(discountTotal));
        cursor.y -= 18;
        text(cursor, bold, 12, 380, "Grand Total:");
        text(cursor, bold, 12, 480, formatCurrency(quotation.getGrandTotal()));

        if (quotation.getNotes() != null && !quotation.getNotes().isBlank()) {
            cursor.y -= 30;
            text(cursor, bold, 10, MARGIN, "Notes");
            cursor.y -= 14;
            text(cursor, regular, 9, MARGIN, quotation.getNotes());
        }
    }

    private void drawTableRow(Cursor cursor, PDFont font, float[] columnX, String[] values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            text(cursor, font, 9, columnX[i], values[i]);
        }
        cursor.y -= 16;
    }

    private void line(Cursor cursor) throws IOException {
        cursor.content.moveTo(MARGIN, cursor.y);
        cursor.content.lineTo(PAGE_WIDTH - MARGIN, cursor.y);
        cursor.content.stroke();
    }

    private void text(Cursor cursor, PDFont font, float size, float x, String value) throws IOException {
        cursor.content.beginText();
        cursor.content.setFont(font, size);
        cursor.content.newLineAtOffset(x, cursor.y);
        cursor.content.showText(value == null ? "" : value);
        cursor.content.endText();
    }

    /** Replaces characters the standard PDF font can't draw (e.g. ₹, emoji) so user-entered text can't break the PDF. */
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

    private static Color brandColor(String hex) {
        if (hex == null) {
            return Color.BLACK;
        }
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    private String formatCurrency(BigDecimal value) {
        return "Rs. " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
