package com.company.crm.analytics.export;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * FR-9.7: writes a {@link ReportDocument} as an Excel workbook — a Summary sheet with the title,
 * period and generation time, then one sheet per table. The OpenXML package is small enough to
 * write directly, so there's no spreadsheet library dependency.
 */
@Component
public class XlsxWriter {

    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] write(ReportDocument doc) {
        List<String> names = sheetNames(doc);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "[Content_Types].xml", contentTypes(names.size()));
            entry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
                    </Relationships>""");
            entry(zip, "xl/workbook.xml", workbook(names));
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels(names.size()));
            entry(zip, "xl/styles.xml", STYLES);

            StringBuilder summary = new StringBuilder();
            summary.append(row(1, List.of(doc.title()), 1));
            summary.append(row(2, List.of(doc.subtitle()), 0));
            summary.append(row(3, List.of("Generated " + GENERATED.format(doc.generatedAt())), 0));
            entry(zip, "xl/worksheets/sheet1.xml", sheet(summary.toString()));
            for (int i = 0; i < doc.tables().size(); i++) {
                ReportDocument.Table table = doc.tables().get(i);
                StringBuilder rows = new StringBuilder(row(1, List.copyOf(table.headers()), 1));
                for (int r = 0; r < table.rows().size(); r++) {
                    rows.append(row(r + 2, table.rows().get(r), 0));
                }
                entry(zip, "xl/worksheets/sheet" + (i + 2) + ".xml", sheet(rows.toString()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** "Summary" plus each table's title — trimmed to Excel's 31 characters, made unique. */
    private static List<String> sheetNames(ReportDocument doc) {
        Set<String> used = new HashSet<>(Set.of("summary"));
        List<String> names = new java.util.ArrayList<>(List.of("Summary"));
        for (ReportDocument.Table t : doc.tables()) {
            String base = t.title().replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
            base = base.isEmpty() ? "Sheet" : base.substring(0, Math.min(28, base.length()));
            String name = base;
            for (int n = 2; !used.add(name.toLowerCase()); n++) {
                name = base + " " + n;
            }
            names.add(name);
        }
        return names;
    }

    private static String row(int index, List<Object> cells, int style) {
        StringBuilder out = new StringBuilder("<row r=\"").append(index).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            Object v = cells.get(c);
            String ref = column(c) + index;
            if (v == null) {
                continue;
            }
            if (v instanceof Number n) {
                out.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"><v>")
                        .append(n instanceof java.math.BigDecimal b ? b.toPlainString() : n.toString()).append("</v></c>");
            } else {
                out.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(v.toString())).append("</t></is></c>");
            }
        }
        return out.append("</row>").toString();
    }

    private static String column(int index) {
        StringBuilder s = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            s.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return s.toString();
    }

    private static String sheet(String rows) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>" + rows + "</sheetData></worksheet>";
    }

    private static String workbook(List<String> names) {
        StringBuilder sheets = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            sheets.append("<sheet name=\"").append(escape(names.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>" + sheets + "</sheets></workbook>";
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder rels = new StringBuilder();
        for (int i = 1; i <= sheetCount; i++) {
            rels.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        rels.append("<Relationship Id=\"rId").append(sheetCount + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + rels + "</Relationships>";
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder sheets = new StringBuilder();
        for (int i = 1; i <= sheetCount; i++) {
            sheets.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + sheets + "</Types>";
    }

    /** Style 0 = normal, style 1 = bold (headers and the title). */
    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
            + "</styleSheet>";

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** XML-escapes text and drops control characters XML 1.0 can't carry. */
    private static String escape(String v) {
        return v.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
