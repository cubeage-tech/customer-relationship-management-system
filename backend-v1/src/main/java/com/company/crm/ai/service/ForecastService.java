package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.Forecast;
import com.company.crm.ai.dto.AiDtos.ForecastDeal;
import com.company.crm.common.exception.ApiException;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.entity.PipelineStage;
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
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

/**
 * FR-AI-2 / API-G11: revenue forecast for this or the next month/quarter, from the caller's
 * pipeline. Each open deal expected to close in the period is weighted by its chance of closing —
 * the tenant's win rate over the last year, lower in early stages and higher in late ones — and
 * added to what is already won in the period. Compared with the previous period and the sales
 * targets (FR-AI-2.3).
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final OpportunityService opportunityService;
    private final PipelineStageService pipelineStageService;
    private final SalesTargetService targetService;

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
        List<String> openStages = pipelineStageService.stages(tenantId).stream()
                .filter(s -> s.isActive() && !PipelineStageService.isTerminal(s.getCode()))
                .map(PipelineStage::getCode).toList();

        List<OpportunityResDto> deals = opportunityService.listOpportunities(user, null, null, null);
        List<ForecastDeal> weighted = deals.stream()
                .filter(o -> !PipelineStageService.isTerminal(o.getStage()))
                .filter(o -> o.getExpectedClosingDate() != null && !o.getExpectedClosingDate().isBefore(from) && !o.getExpectedClosingDate().isAfter(to))
                .map(o -> {
                    double prob = probability(winRate, openStages.indexOf(o.getStage()), openStages.size());
                    BigDecimal value = o.getDealValue() != null ? o.getDealValue() : BigDecimal.ZERO;
                    return new ForecastDeal(o.getId(), o.getCustomerName(), money(value), o.getStage(), o.getExpectedClosingDate(),
                            Math.round(prob * 100) / 100.0, money(value.multiply(BigDecimal.valueOf(prob))));
                })
                .sorted(Comparator.comparing(ForecastDeal::weightedValue).reversed())
                .toList();

        BigDecimal pipeline = money(weighted.stream().map(ForecastDeal::weightedValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal wonSoFar = won(deals, from, to.isAfter(today) ? today : to);
        BigDecimal expected = money(wonSoFar.add(pipeline));
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        BigDecimal previous = won(deals, from.minusDays(days), from.minusDays(1));
        Double growth = previous.signum() == 0 ? null
                : expected.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP).doubleValue();

        BigDecimal target = targetService.list(user).stream()
                .filter(t -> "revenue".equals(t.metric()) && !t.periodEnd().isBefore(from) && !t.periodStart().isAfter(to))
                .map(TargetRes::targetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        Double coverage = target.signum() == 0 ? null
                : expected.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP).doubleValue();

        return new Forecast(p, from, to, wonSoFar, pipeline, expected, previous, growth, target.signum() == 0 ? null : money(target),
                coverage, Math.round(winRate * 1000) / 1000.0, weighted.stream().limit(20).toList(), LocalDateTime.now());
    }

    /** Chance of closing in open stage {@code index} of {@code count}: from half to 1.5× the win rate. */
    static double probability(double winRate, int index, int count) {
        double position = count <= 1 || index < 0 ? 0.5 : (double) index / (count - 1);
        return Math.min(0.95, winRate * (0.5 + position));
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
