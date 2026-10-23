package com.company.crm.analytics.service;

import com.company.crm.analytics.dto.AnalyticsDtos.*;
import com.company.crm.analytics.entity.DashboardPreference;
import com.company.crm.analytics.repository.AnalyticsQuery;
import com.company.crm.analytics.repository.AnalyticsQuery.Bucket;
import com.company.crm.analytics.repository.DashboardPreferenceRepository;
import com.company.crm.campaign.service.SegmentService;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.industry.service.IndustryService;
import com.company.crm.sales.entity.PipelineStage;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.salesforce.dto.SalesForceDtos.TargetRes;
import com.company.crm.salesforce.repository.SalesForceQuery;
import com.company.crm.salesforce.service.SalesForceScope;
import com.company.crm.salesforce.service.SalesTargetService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FR-9.1–9.5: the dashboard headline and the Sales, Customer, Revenue and Team Performance reports.
 * Every figure is computed on request from the live data (generatedAt says when), narrowed by the
 * FR-9.6 filters and the caller's data scope.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /** Headline tiles a user can pick for their dashboard, in default order. */
    public static final List<String> WIDGETS = List.of("totalLeads", "convertedLeads", "conversionRate", "revenue",
            "revenueGrowth", "leadGrowth", "wonDeals", "openPipeline");
    private static final Set<String> PERIODS = Set.of("month", "quarter", "year", "last30");
    private static final Set<RoleType> SALES_ROLES = Set.of(RoleType.SALES_EXECUTIVE, RoleType.SALES_MANAGER);

    private final AnalyticsQuery query;
    private final SalesForceQuery salesForceQuery;
    private final SalesForceScope salesForceScope;
    private final SalesTargetService targetService;
    private final PipelineStageService pipelineStageService;
    private final IndustryService industryService;
    private final SegmentService segmentService;
    private final DashboardPreferenceRepository preferenceRepository;

    // ==================== FR-9.1 ====================

    @Transactional(readOnly = true)
    public Headline headline(User user, ReportScope s) {
        Totals current = totals(s);
        Totals previous = totals(s.previous());
        Bucket open = query.openPipeline(s);
        return new Headline(s.from(), s.to(), current, previous,
                growth(BigDecimal.valueOf(current.totalLeads()), BigDecimal.valueOf(previous.totalLeads())),
                growth(current.revenue(), previous.revenue()),
                open.value(), open.count(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Preferences preferences(User user) {
        Preferences p = new Preferences();
        Optional<DashboardPreference> saved = preferenceRepository.findById(user.getId());
        p.setWidgets(saved.map(d -> List.of(d.getWidgets().split(","))).orElse(WIDGETS.subList(0, 6)));
        p.setPeriod(saved.map(DashboardPreference::getPeriod).orElse("month"));
        return p;
    }

    @Transactional
    public Preferences savePreferences(User user, Preferences dto) {
        List<String> widgets = dto.getWidgets().stream().distinct().toList();
        if (!WIDGETS.containsAll(widgets)) {
            throw ApiException.badRequest("Unknown dashboard tile");
        }
        if (!PERIODS.contains(dto.getPeriod())) {
            throw ApiException.badRequest("Unknown period");
        }
        DashboardPreference p = preferenceRepository.findById(user.getId()).orElseGet(DashboardPreference::new);
        p.setUserId(user.getId());
        p.setWidgets(String.join(",", widgets));
        p.setPeriod(dto.getPeriod());
        preferenceRepository.save(p);
        return preferences(user);
    }

    // ==================== FR-9.2 ====================

    @Transactional(readOnly = true)
    public SalesReport sales(User user, ReportScope s) {
        Map<String, String> stageLabels = pipelineStageService.stages(s.tenantId()).stream()
                .collect(Collectors.toMap(PipelineStage::getCode, PipelineStage::getLabel));
        List<Row> byStage = query.opportunitiesByStage(s).stream()
                .map(b -> new Row(b.key(), stageLabels.getOrDefault(b.key(), b.key()), b.count(), b.value()))
                .sorted(Comparator.comparing(Row::value).reversed())
                .toList();

        // Keyed by owner id (null = unassigned); names aren't unique.
        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, BigDecimal[]> values = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (Object[] r : query.opportunitiesByOwnerAndStage(s)) {
            Long id = r[0] == null ? null : ((Number) r[0]).longValue();
            names.put(id, String.valueOf(r[1]));
            int slot = PipelineStageService.WON.equals(r[2]) ? 1 : PipelineStageService.LOST.equals(r[2]) ? 2 : 0;
            counts.computeIfAbsent(id, k -> new long[3])[slot] += ((Number) r[3]).longValue();
            BigDecimal[] v = values.computeIfAbsent(id, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            v[slot] = v[slot].add(new BigDecimal(r[4].toString()));
        }
        List<OwnerRow> byOwner = counts.keySet().stream().map(id -> {
            long[] c = counts.get(id);
            BigDecimal[] v = values.get(id);
            return new OwnerRow(id, names.get(id), c[0], money(v[0]), c[1], money(v[1]), c[2], money(v[2]),
                    c[0] + c[1] + c[2], money(v[0].add(v[1]).add(v[2])));
        }).sorted(Comparator.comparing(OwnerRow::totalValue).reversed()).toList();

        return new SalesReport(s.from(), s.to(), byStage.stream().mapToLong(Row::count).sum(),
                money(byStage.stream().map(Row::value).reduce(BigDecimal.ZERO, BigDecimal::add)), byStage, byOwner, LocalDateTime.now());
    }

    // ==================== FR-9.3 ====================

    @Transactional(readOnly = true)
    public CustomerReport customers(User user, ReportScope s) {
        Map<String, String> industries = industryLabels(user);
        long[] counts = query.customerCounts(s);
        long contacted = query.customersContacted(s);
        List<Row> byIndustry = labelled(query.customersByIndustry(s), industries);
        List<Row> activity = query.activityByChannel(s).stream()
                .map(b -> new Row(b.key(), capitalize(b.key()), b.count(), BigDecimal.ZERO)).toList();
        // FR-7.2 marketing segments — tenant-wide audiences, so only for viewers who see the whole tenant.
        List<Row> segments = s.ownerIds() == null && s.industry() == null
                ? segmentService.list(user).stream().map(g -> new Row(String.valueOf(g.id()), g.name(), g.customerCount(), BigDecimal.ZERO)).toList()
                : List.of();
        return new CustomerReport(s.from(), s.to(), counts[0], counts[1], contacted, Math.max(0, counts[0] - contacted),
                byIndustry, activity, segments, LocalDateTime.now());
    }

    // ==================== FR-9.4 ====================

    @Transactional(readOnly = true)
    public RevenueReport revenue(User user, ReportScope s) {
        Bucket won = query.won(s);
        List<Row> byMonth = query.revenueByMonth(s).stream().map(b -> new Row(b.key(), b.key(), b.count(), b.value())).toList();
        List<Row> byIndustry = labelled(query.revenueByIndustry(s), industryLabels(user));
        List<Row> byProduct = query.revenueByProduct(s).stream().map(b -> new Row(b.key(), b.key(), b.count(), b.value())).toList();
        List<Row> bySalesperson = query.revenueBySalesperson(s).stream()
                .map(r -> new Row(r[0] == null ? null : r[0].toString(), String.valueOf(r[1]), ((Number) r[2]).longValue(), money(new BigDecimal(r[3].toString()))))
                .toList();
        return new RevenueReport(s.from(), s.to(), won.value(), won.count(), byMonth, byIndustry, byProduct, bySalesperson, LocalDateTime.now());
    }

    // ==================== FR-9.5 ====================

    @Transactional(readOnly = true)
    public TeamPerformance teamPerformance(User user, ReportScope s) {
        List<User> people = salesForceScope.visibleUsers(user).stream()
                .filter(u -> SALES_ROLES.contains(u.getRole().getName()))
                .filter(u -> s.ownerIds() == null || s.ownerIds().contains(u.getId()))
                .sorted(Comparator.comparing(User::getFullName))
                .toList();
        List<Long> ids = people.stream().map(User::getId).toList();
        Map<Long, Bucket> won = query.wonByOwner(s);
        Map<Long, BigDecimal> commission = query.commissionByUser(s);
        Map<Long, Long> calls = salesForceQuery.calls(s.tenantId(), ids, s.start(), s.end());
        Map<Long, Long> meetings = salesForceQuery.meetings(s.tenantId(), ids, s.start(), s.end());
        Map<Long, Long> quotations = salesForceQuery.quotationsSent(s.tenantId(), ids, s.start(), s.end());
        Map<Long, Long> visits = salesForceQuery.visits(s.tenantId(), ids, s.start(), s.end());
        // Revenue targets whose period overlaps the report period (each measured over its own period).
        List<TargetRes> targets = targetService.list(user).stream()
                .filter(t -> "revenue".equals(t.metric()) && !t.periodEnd().isBefore(s.from()) && !t.periodStart().isAfter(s.to()))
                .toList();
        Map<Long, String> teamNames = salesForceScope.visibleTeams(user).stream().collect(Collectors.toMap(SalesTeam::getId, SalesTeam::getName));

        List<PersonRow> rows = people.stream().map(u -> {
            Bucket w = won.get(u.getId());
            List<TargetRes> mine = targets.stream().filter(t -> u.getId().equals(t.userId())).toList();
            BigDecimal[] att = attainment(mine);
            return new PersonRow(u.getId(), u.getFullName(), u.getTeamId(), u.getTeamId() != null ? teamNames.get(u.getTeamId()) : null,
                    w != null ? w.value() : money(BigDecimal.ZERO), w != null ? w.count() : 0,
                    att[0], att[1], percent(att),
                    calls.getOrDefault(u.getId(), 0L), meetings.getOrDefault(u.getId(), 0L),
                    quotations.getOrDefault(u.getId(), 0L), visits.getOrDefault(u.getId(), 0L),
                    money(commission.getOrDefault(u.getId(), BigDecimal.ZERO)));
        }).toList();

        List<TeamRow> teams = salesForceScope.visibleTeams(user).stream().map(team -> {
            List<PersonRow> members = rows.stream().filter(r -> team.getId().equals(r.teamId())).toList();
            BigDecimal[] att = attainment(targets.stream().filter(t -> team.getId().equals(t.teamId())).toList());
            return new TeamRow(team.getId(), team.getName(), members.size(),
                    money(members.stream().map(PersonRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add)),
                    members.stream().mapToLong(PersonRow::wonDeals).sum(),
                    money(members.stream().map(PersonRow::commission).reduce(BigDecimal.ZERO, BigDecimal::add)),
                    att[0], att[1], percent(att));
        }).filter(t -> t.members() > 0 || t.targetValue() != null).toList();

        return new TeamPerformance(s.from(), s.to(), rows, teams, LocalDateTime.now());
    }

    // ==================== FR-9.6 ====================

    /** The filter choices the caller may use: their visible teams and salespeople, and the industries. */
    @Transactional
    public FilterOptions filterOptions(User user) {
        List<Option> teams = salesForceScope.visibleTeams(user).stream().map(t -> new Option(t.getId(), null, t.getName())).toList();
        List<Option> users = salesForceScope.visibleUsers(user).stream()
                .filter(u -> SALES_ROLES.contains(u.getRole().getName()))
                .sorted(Comparator.comparing(User::getFullName))
                .map(u -> new Option(u.getId(), null, u.getFullName())).toList();
        List<Option> industries = industryService.list(user, true).stream().map(i -> new Option(null, i.code(), i.label())).toList();
        return new FilterOptions(teams, users, industries);
    }

    // ==================== Helpers ====================

    private Totals totals(ReportScope s) {
        long[] leads = query.leads(s);
        Bucket won = query.won(s);
        double rate = leads[0] == 0 ? 0.0 : Math.round(leads[1] * 1000.0 / leads[0]) / 10.0;
        return new Totals(leads[0], leads[1], rate, won.value(), won.count());
    }

    /** % change from previous to current; null when there's nothing to compare with. */
    private static Double growth(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP).doubleValue();
    }

    /** [sum of targets, sum of their actuals], or [null, null] when there are none. */
    private static BigDecimal[] attainment(List<TargetRes> targets) {
        if (targets.isEmpty()) {
            return new BigDecimal[]{null, null};
        }
        return new BigDecimal[]{
                money(targets.stream().map(TargetRes::targetValue).reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(targets.stream().map(TargetRes::actual).reduce(BigDecimal.ZERO, BigDecimal::add))};
    }

    private static Double percent(BigDecimal[] att) {
        return att[0] == null || att[0].signum() == 0 ? null
                : att[1].multiply(BigDecimal.valueOf(100)).divide(att[0], 1, RoundingMode.HALF_UP).doubleValue();
    }

    private Map<String, String> industryLabels(User user) {
        return industryService.list(user, true).stream().collect(Collectors.toMap(i -> i.code(), i -> i.label(), (a, b) -> a));
    }

    private static List<Row> labelled(List<Bucket> buckets, Map<String, String> labels) {
        return buckets.stream().map(b -> new Row(b.key(), labels.getOrDefault(b.key(), b.key()), b.count(), b.value())).toList();
    }

    private static String capitalize(String v) {
        return v == null || v.isEmpty() ? v : Character.toUpperCase(v.charAt(0)) + v.substring(1).replace('_', ' ');
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
