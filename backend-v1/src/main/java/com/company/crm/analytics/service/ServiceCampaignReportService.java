package com.company.crm.analytics.service;

import com.company.crm.analytics.dto.AnalyticsDtos.CampaignReport;
import com.company.crm.analytics.dto.AnalyticsDtos.CampaignRow;
import com.company.crm.analytics.dto.AnalyticsDtos.ServiceReport;
import com.company.crm.analytics.dto.AnalyticsDtos.ServiceRow;
import com.company.crm.analytics.repository.ServiceReportQuery;
import com.company.crm.analytics.repository.ServiceReportQuery.Filter;
import com.company.crm.campaign.dto.response.CampaignMetricsDto;
import com.company.crm.campaign.dto.response.CampaignResDto;
import com.company.crm.campaign.service.CampaignService;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.enums.TicketStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.salesforce.service.SalesForceScope;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** REP-SVC and REP-CMP, within the caller's scope (REP-SCOPE). */
@Service
@RequiredArgsConstructor
public class ServiceCampaignReportService {

    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "critical");
    private static final List<String> OPEN_STATUSES = EnumSet.of(TicketStatus.OPEN, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS)
            .stream().map(TicketStatus::getDbValue).toList();

    private final ServiceReportQuery serviceQuery;
    private final CampaignService campaignService;
    private final SalesForceScope salesForceScope;
    private final UserRepository userRepository;

    // ==================== REP-SVC ====================

    /**
     * Ticket volume, resolution time, SLA compliance and feedback, by priority and technician.
     * Service agents only ever see the tickets assigned to them.
     */
    @Transactional(readOnly = true)
    public ServiceReport service(User user, ReportScope period, String priority, Long technicianId) {
        Long tenantId = salesForceScope.tenantId(user);
        if (user.getRole().getName() == RoleType.SERVICE_AGENT) {
            if (technicianId != null && !technicianId.equals(user.getId())) {
                throw ApiException.forbidden("You can only report on your own tickets");
            }
            technicianId = user.getId();
        } else if (technicianId != null && userRepository.findByIdAndTenantId(technicianId, tenantId).isEmpty()) {
            throw ApiException.forbidden("You can't report on that user");
        }
        String p = priority == null || priority.isBlank() ? null : priority.trim();
        if (p != null && !PRIORITIES.contains(p)) {
            throw ApiException.badRequest("Unknown priority");
        }
        Filter f = new Filter(tenantId, technicianId, p, period.start(), period.end());

        Object[] raw = serviceQuery.aggregate(f, null).get(0); // no GROUP BY: always exactly one row
        ServiceRow total = row(raw);
        long withinSla = num(raw[5]);
        long feedbackCount = num(raw[6]);
        return new ServiceReport(period.from(), period.to(), total.created(), total.resolved(), total.avgResolutionHours(),
                withinSla, total.slaCompliancePercent(), serviceQuery.openBreached(f, OPEN_STATUSES, LocalDateTime.now()),
                feedbackCount, total.averageFeedback(),
                serviceQuery.aggregate(f, "priority").stream().map(ServiceCampaignReportService::row).toList(),
                serviceQuery.aggregate(f, "technician").stream().map(ServiceCampaignReportService::row).toList(),
                LocalDateTime.now());
    }

    // ==================== REP-CMP ====================

    /** Sent / opened / clicked / converted per campaign running at any point in the period. */
    @Transactional(readOnly = true)
    public CampaignReport campaigns(User user, ReportScope period) {
        List<CampaignRow> rows = campaignService.listCampaigns(user, null, null, null).stream()
                .filter(c -> overlaps(c, period.from(), period.to()))
                .map(c -> {
                    CampaignMetricsDto m = campaignService.metrics(user, c.getId());
                    BigDecimal cost = c.getActualCost() != null ? c.getActualCost().setScale(2, RoundingMode.HALF_UP) : null;
                    BigDecimal perLead = cost == null || m.leadsGenerated() == 0 ? null
                            : cost.divide(BigDecimal.valueOf(m.leadsGenerated()), 2, RoundingMode.HALF_UP);
                    return new CampaignRow(c.getId(), c.getName(), c.getChannel(), c.getStatus(), c.getStartDate(), c.getEndDate(),
                            m.sent(), m.opened(), m.clicked(), m.converted(), m.openRate(), m.clickRate(), m.conversionRate(),
                            m.leadsGenerated(), m.convertedLeads(), cost, perLead);
                })
                .toList();
        return new CampaignReport(period.from(), period.to(),
                rows.stream().mapToLong(CampaignRow::sent).sum(), rows.stream().mapToLong(CampaignRow::opened).sum(),
                rows.stream().mapToLong(CampaignRow::clicked).sum(), rows.stream().mapToLong(CampaignRow::converted).sum(),
                rows.stream().mapToLong(CampaignRow::leadsGenerated).sum(), rows, LocalDateTime.now());
    }

    /** A campaign counts for the period if it ran during it (start defaults to when it was created). */
    private static boolean overlaps(CampaignResDto c, LocalDate from, LocalDate to) {
        LocalDate start = c.getStartDate() != null ? c.getStartDate() : c.getCreatedAt().toLocalDate();
        return !start.isAfter(to) && (c.getEndDate() == null || !c.getEndDate().isBefore(from));
    }

    private static ServiceRow row(Object[] r) {
        long resolved = num(r[3]);
        Double compliance = resolved == 0 ? null : Math.round(num(r[5]) * 1000.0 / resolved) / 10.0;
        return new ServiceRow(r[0] == null ? null : r[0].toString(), String.valueOf(r[1]), num(r[2]), resolved,
                round(r[4]), compliance, round(r[7]));
    }

    private static Double round(Object v) {
        return v == null ? null : Math.round(((Number) v).doubleValue() * 10) / 10.0;
    }

    private static long num(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }
}
