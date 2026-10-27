package com.company.crm.analytics.export;

import com.company.crm.analytics.dto.AnalyticsDtos.*;
import com.company.crm.analytics.export.ReportDocument.Table;
import com.company.crm.analytics.service.AnalyticsService;
import com.company.crm.analytics.service.ReportScope;
import com.company.crm.analytics.service.ReportScopeResolver;
import com.company.crm.analytics.service.ReportType;
import com.company.crm.analytics.service.ServiceCampaignReportService;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * FR-9.7 / REP-SCH: runs any report as a given user and lays it out as a {@link ReportDocument}.
 * The role check (REP-SCOPE) and the data scope are the same as for viewing the report, so an
 * export or a scheduled email never shows more than the user could see on screen.
 */
@Service
@RequiredArgsConstructor
public class ReportRunner {

    /** Filters common to every report; the service report also uses priority and technicianId. */
    public record Filters(LocalDate from, LocalDate to, String industry, Long teamId, Long userId,
                          String priority, Long technicianId) {}

    public enum Format {
        PDF("pdf", "application/pdf"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        public final String extension;
        public final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public static Format fromCode(String code) {
            return Arrays.stream(values()).filter(f -> f.extension.equalsIgnoreCase(code)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Format must be pdf or xlsx"));
        }
    }

    public record Rendered(String filename, String contentType, byte[] bytes) {}

    private final ReportScopeResolver scopeResolver;
    private final AnalyticsService analyticsService;
    private final ServiceCampaignReportService serviceCampaignReports;
    private final ReportPdfWriter pdfWriter;
    private final XlsxWriter xlsxWriter;

    @Transactional(readOnly = true)
    public Rendered render(User user, ReportType type, Filters f, Format format) {
        ReportDocument doc = document(user, type, f);
        byte[] bytes = format == Format.PDF ? pdfWriter.write(doc) : xlsxWriter.write(doc);
        String name = type.getCode() + "-report-" + f.from() + "-to-" + f.to() + "." + format.extension;
        return new Rendered(name, format.contentType, bytes);
    }

    @Transactional(readOnly = true)
    public ReportDocument document(User user, ReportType type, Filters f) {
        if (user.getRole() == null || !type.allows(user.getRole().getName())) {
            throw ApiException.forbidden("Your role can't run the " + type.getTitle().toLowerCase());
        }
        boolean salesFilters = type != ReportType.SERVICE && type != ReportType.CAMPAIGNS;
        ReportScope s = scopeResolver.resolve(user, f.from(), f.to(),
                salesFilters ? f.industry() : null, salesFilters ? f.teamId() : null, salesFilters ? f.userId() : null);
        String subtitle = s.from() + " to " + s.to();
        return switch (type) {
            case SALES -> sales(analyticsService.sales(user, s), subtitle);
            case CUSTOMERS -> customers(analyticsService.customers(user, s), subtitle);
            case REVENUE -> revenue(analyticsService.revenue(user, s), subtitle);
            case TEAM -> team(analyticsService.teamPerformance(user, s), subtitle);
            case SERVICE -> service(serviceCampaignReports.service(user, s, f.priority(), f.technicianId()), subtitle);
            case CAMPAIGNS -> campaigns(serviceCampaignReports.campaigns(user, s), subtitle);
            case LEAD_SOURCES -> leadSources(analyticsService.leadSources(user, s), subtitle);
        };
    }

    // ==================== Layouts ====================

    private static ReportDocument sales(SalesReport r, String subtitle) {
        return doc(ReportType.SALES, subtitle, r.generatedAt(),
                table("Summary", List.of("Opportunities created", "Total value"), List.of(Arrays.asList(r.totalCount(), r.totalValue()))),
                rows("By stage", r.byStage()),
                table("By owner", List.of("Owner", "Open", "Open value", "Won", "Won value", "Lost", "Lost value", "Total", "Total value"),
                        map(r.byOwner(), o -> Arrays.asList(o.ownerName(), o.openCount(), o.openValue(), o.wonCount(), o.wonValue(),
                                o.lostCount(), o.lostValue(), o.totalCount(), o.totalValue()))));
    }

    private static ReportDocument customers(CustomerReport r, String subtitle) {
        List<Table> tables = new ArrayList<>(List.of(
                table("Summary", List.of("Active customers", "New in period", "Contacted", "Not contacted"),
                        List.of(Arrays.asList(r.activeCustomers(), r.newCustomers(), r.contactedCustomers(), r.notContactedCustomers()))),
                counts("By industry", r.byIndustry()),
                counts("Activity by channel", r.activityByChannel())));
        if (!r.segments().isEmpty()) {
            tables.add(counts("Marketing segments", r.segments()));
        }
        return new ReportDocument(ReportType.CUSTOMERS.getTitle(), subtitle, r.generatedAt(), tables);
    }

    private static ReportDocument revenue(RevenueReport r, String subtitle) {
        return doc(ReportType.REVENUE, subtitle, r.generatedAt(),
                table("Summary", List.of("Revenue", "Deals won"), List.of(Arrays.asList(r.totalRevenue(), r.wonDeals()))),
                rows("By month", r.byMonth()), rows("By product", r.byProduct()),
                rows("By industry", r.byIndustry()), rows("By salesperson", r.bySalesperson()));
    }

    private static ReportDocument team(TeamPerformance r, String subtitle) {
        return doc(ReportType.TEAM, subtitle, r.generatedAt(),
                table("Salespeople", List.of("Name", "Team", "Revenue", "Won", "Target", "Attainment %", "Calls", "Meetings",
                                "Quotations sent", "Visits", "Commission"),
                        map(r.people(), p -> Arrays.asList(p.name(), p.teamName(), p.revenue(), p.wonDeals(), p.targetValue(),
                                p.attainmentPercent(), p.calls(), p.meetings(), p.quotationsSent(), p.visits(), p.commission()))),
                table("Teams", List.of("Team", "Members", "Revenue", "Won", "Target", "Attainment %", "Commission"),
                        map(r.teams(), t -> Arrays.asList(t.name(), t.members(), t.revenue(), t.wonDeals(), t.targetValue(),
                                t.attainmentPercent(), t.commission()))));
    }

    private static ReportDocument service(ServiceReport r, String subtitle) {
        List<String> head = List.of("", "Raised", "Resolved", "Avg resolution (h)", "SLA compliance %", "Avg feedback");
        Function<ServiceRow, List<Object>> line = x -> Arrays.asList(x.label(), x.created(), x.resolved(), x.avgResolutionHours(),
                x.slaCompliancePercent(), x.averageFeedback());
        return doc(ReportType.SERVICE, subtitle, r.generatedAt(),
                table("Summary", List.of("Raised", "Resolved", "Avg resolution (h)", "Resolved within SLA", "SLA compliance %",
                                "Open past SLA", "Ratings", "Avg feedback"),
                        List.of(Arrays.asList(r.created(), r.resolved(), r.avgResolutionHours(), r.resolvedWithinSla(),
                                r.slaCompliancePercent(), r.openBreached(), r.feedbackCount(), r.averageFeedback()))),
                table("By priority", head, map(r.byPriority(), line)),
                table("By technician", head, map(r.byTechnician(), line)));
    }

    private static ReportDocument campaigns(CampaignReport r, String subtitle) {
        return doc(ReportType.CAMPAIGNS, subtitle, r.generatedAt(),
                table("Summary", List.of("Sent", "Opened", "Clicked", "Converted", "Leads generated"),
                        List.of(Arrays.asList(r.sent(), r.opened(), r.clicked(), r.converted(), r.leadsGenerated()))),
                table("Campaigns", List.of("Campaign", "Channel", "Status", "Sent", "Opened", "Clicked", "Converted", "Open %",
                                "Click %", "Conversion %", "Leads", "Converted leads", "Cost", "Cost per lead"),
                        map(r.campaigns(), c -> Arrays.asList(c.name(), c.channel(), c.status(), c.sent(), c.opened(), c.clicked(),
                                c.converted(), c.openRate(), c.clickRate(), c.conversionRate(), c.leadsGenerated(),
                                c.convertedLeads(), c.actualCost(), c.costPerLead()))));
    }

    private static ReportDocument leadSources(LeadSourceReport r, String subtitle) {
        return doc(ReportType.LEAD_SOURCES, subtitle, r.generatedAt(),
                table("Summary", List.of("Leads", "Converted", "Conversion %"),
                        List.of(Arrays.asList(r.totalLeads(), r.convertedLeads(), r.conversionRate()))),
                table("By source", List.of("Source", "Leads", "Converted", "Conversion %"),
                        map(r.bySource(), x -> Arrays.asList(x.label(), x.leads(), x.converted(), x.conversionRate()))));
    }

    // ==================== Helpers ====================

    private static ReportDocument doc(ReportType type, String subtitle, LocalDateTime generatedAt, Table... tables) {
        return new ReportDocument(type.getTitle(), subtitle, generatedAt, List.of(tables));
    }

    private static Table table(String title, List<String> headers, List<List<Object>> rows) {
        return new Table(title, headers, rows);
    }

    private static Table rows(String title, List<Row> rows) {
        return table(title, List.of("", "Count", "Value"), map(rows, r -> Arrays.asList(r.label(), r.count(), r.value())));
    }

    private static Table counts(String title, List<Row> rows) {
        return table(title, List.of("", "Count"), map(rows, r -> Arrays.asList(r.label(), r.count())));
    }

    private static <T> List<List<Object>> map(List<T> items, Function<T, List<Object>> line) {
        return items.stream().map(line).toList();
    }
}
