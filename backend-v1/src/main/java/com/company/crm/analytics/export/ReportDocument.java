package com.company.crm.analytics.export;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-9.7: a report as plain titled tables, ready to render as PDF or XLSX. Cell values are
 * Strings or Numbers (numbers stay numeric in Excel); null renders as an empty cell.
 */
public record ReportDocument(String title, String subtitle, LocalDateTime generatedAt, List<Table> tables) {

    public record Table(String title, List<String> headers, List<List<Object>> rows) {}
}
