package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.Forecast;
import com.company.crm.ai.dto.AiDtos.ForecastDeal;
import com.company.crm.ai.dto.AiDtos.PeriodRevenue;
import com.company.crm.common.exception.ApiException;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.salesforce.dto.SalesForceDtos.TargetRes;
import com.company.crm.salesforce.service.SalesTargetService;
import com.company.crm.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FR-AI-2.1 / API-G11: revenue forecast for this or the next month/quarter, from history and the
 * caller's pipeline: what is already won in the period, plus each open deal expected to close in it
 * weighted by its chance of closing (FR-AI-2.2), plus new business not in the pipeline yet — the
 * recent run rate of deals that were created and won within one period. Comes with a likely range,
 * the six periods before it, growth on the previous period, and (FR-AI-2.3) coverage of and variance
 * from the sales targets.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final OpportunityService opportunityService;
    private final PipelineStageService pipelineStageService;
    private final SalesTargetService targetService;
    private final DealProbabilityService dealProbabilities;

    private static final int HISTORY_PERIODS = 6;
    private static final int RUN_RATE_DAYS = 180;
    private static final double Z = 1.28; // ~80% range

    @PersistenceContext
    private EntityManager entityManager;

    // Not read-only: the first look at a new tenant's pipeline seeds its default stages.
    @Transactional
    public Forecast forecast(User user, String period) {
        String p = period == null || period.isBlank() ? "next_month" : period;
        LocalDate today = LocalDate.now();
        LocalDate[] range = range(p, today);
        LocalDate from = range[0];
        LocalDate to = range[1];
        Long tenantId = user.getTenant().getId();

        double winRate = winRate(tenantId, today);
        pipelineStageService.stages(tenantId); // seeds a new tenant's default stages

        List<OpportunityResDto> deals = opportunityService.listOpportunities(user, null, null, null);
        // FR-AI-2.2: the same per-deal chance the deal pages show.
        List<OpportunityResDto> closingInPeriod = deals.stream()
                .filter(o -> o.getExpectedClosingDate() != null && !o.getExpectedClosingDate().isBefore(from) && !o.getExpectedClosingDate().isAfter(to))
                .toList();
        List<ForecastDeal> weighted = dealProbabilities.score(tenantId, closingInPeriod).stream()
                .map(d -> new ForecastDeal(d.opportunityId(), d.customerName(), money(d.dealValue()), d.stage(), d.expectedClosingDate(),
                        d.probability(), money(d.expectedValue())))
                .sorted(Comparator.comparing(ForecastDeal::weightedValue).reversed())
                .toList();

        BigDecimal pipeline = money(weighted.stream().map(ForecastDeal::weightedValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal wonSoFar = won(deals, from, to.isAfter(today) ? today : to);
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        BigDecimal newBusiness = newBusiness(deals, today, from, to, days);
        BigDecimal expected = money(wonSoFar.add(pipeline).add(newBusiness));
        // Likely range: each deal is won or not (variance v²·p·(1−p)).
        double variance = weighted.stream().mapToDouble(d -> {
            double v = d.dealValue().doubleValue();
            return v * v * d.probability() * (1 - d.probability());
        }).sum();
        double spread = Z * Math.sqrt(variance);
        BigDecimal low = money(BigDecimal.valueOf(Math.max(wonSoFar.doubleValue(), expected.doubleValue() - spread)));
        BigDecimal high = money(BigDecimal.valueOf(expected.doubleValue() + spread));
        List<PeriodRevenue> history = new ArrayList<>();
        for (int i = HISTORY_PERIODS; i >= 1; i--) {
            LocalDate start = from.minusDays(days * i);
            LocalDate end = start.plusDays(days - 1);
            history.add(new PeriodRevenue(start, end, won(deals, start, end)));
        }
        BigDecimal previous = won(deals, from.minusDays(days), from.minusDays(1));
        Double growth = previous.signum() == 0 ? null
                : expected.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP).doubleValue();

        BigDecimal target = targetService.list(user).stream()
                .filter(t -> "revenue".equals(t.metric()) && !t.periodEnd().isBefore(from) && !t.periodStart().isAfter(to))
                .map(TargetRes::targetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        Double coverage = target.signum() == 0 ? null
                : expected.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP).doubleValue();
        // FR-AI-2.3: above (+) or below (−) the target, and by how much.
        Double variancePercent = target.signum() == 0 ? null
                : expected.subtract(target).multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP).doubleValue();
        BigDecimal gap = target.signum() == 0 ? null : money(target.subtract(expected).max(BigDecimal.ZERO));

        return new Forecast(p, from, to, wonSoFar, pipeline, expected, previous, growth, target.signum() == 0 ? null : money(target),
                coverage, Math.round(winRate * 1000) / 1000.0, weighted.stream().limit(20).toList(), LocalDateTime.now(),
                newBusiness, low, high, variancePercent, gap, history);
    }

    /**
     * Business not in the pipeline yet: over the last 180 days, the revenue from deals that were
     * created and won within one period, per day — times the days of the period still to come.
     */
    static BigDecimal newBusiness(List<OpportunityResDto> deals, LocalDate today, LocalDate from, LocalDate to, long periodDays) {
        LocalDate remainingFrom = from.isAfter(today) ? from : today.plusDays(1);
        long remaining = Math.max(0, to.toEpochDay() - remainingFrom.toEpochDay() + 1);
        if (remaining == 0) {
            return money(BigDecimal.ZERO);
        }
        LocalDate since = today.minusDays(RUN_RATE_DAYS);
        BigDecimal quick = deals.stream()
                .filter(o -> PipelineStageService.WON.equals(o.getStage()) && o.getStageChangedAt() != null && o.getCreatedAt() != null)
                .filter(o -> !o.getStageChangedAt().toLocalDate().isBefore(since))
                .filter(o -> ChronoUnit.DAYS.between(o.getCreatedAt().toLocalDate(), o.getStageChangedAt().toLocalDate()) < periodDays)
                .map(o -> o.getDealValue() != null ? o.getDealValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return money(quick.multiply(BigDecimal.valueOf(remaining)).divide(BigDecimal.valueOf(RUN_RATE_DAYS), 2, RoundingMode.HALF_UP));
    }

    static LocalDate[] range(String period, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate quarterStart = today.with(IsoFields.DAY_OF_QUARTER, 1);
        return switch (period) {
            case "this_month" -> new LocalDate[]{monthStart, monthStart.with(TemporalAdjusters.lastDayOfMonth())};
            case "next_month" -> {
                LocalDate s = monthStart.plusMonths(1);
                yield new LocalDate[]{s, s.with(TemporalAdjusters.lastDayOfMonth())};
            }
            case "this_quarter" -> new LocalDate[]{quarterStart, quarterStart.plusMonths(3).minusDays(1)};
            case "next_quarter" -> new LocalDate[]{quarterStart.plusMonths(3), quarterStart.plusMonths(6).minusDays(1)};
            default -> throw ApiException.badRequest("Period must be this_month, next_month, this_quarter or next_quarter");
        };
    }

    /** Won ÷ closed deals over the last year, tenant-wide, pulled towards 30% when there are few. */
    private double winRate(Long tenantId, LocalDate today) {
        Object[] row = (Object[]) entityManager.createNativeQuery("SELECT count(*) FILTER (WHERE stage = 'won'), count(*)"
                        + " FROM opportunities WHERE tenant_id = :t AND stage IN ('won', 'lost') AND stage_changed_at >= :since")
                .setParameter("t", tenantId).setParameter("since", today.minusYears(1).atStartOfDay()).getSingleResult();
        long won = ((Number) row[0]).longValue();
        long closed = ((Number) row[1]).longValue();
        return (won + 0.3 * 5) / (closed + 5);
    }

    private static BigDecimal won(List<OpportunityResDto> deals, LocalDate from, LocalDate to) {
        return money(deals.stream()
                .filter(o -> PipelineStageService.WON.equals(o.getStage()) && o.getStageChangedAt() != null)
                .filter(o -> !o.getStageChangedAt().toLocalDate().isBefore(from) && !o.getStageChangedAt().toLocalDate().isAfter(to))
                .map(o -> o.getDealValue() != null ? o.getDealValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
