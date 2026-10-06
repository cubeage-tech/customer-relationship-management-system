package com.company.crm.sales.repository;

import com.company.crm.common.enums.OpportunityStage;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.ScopedJpql;
import com.company.crm.sales.dto.response.OpportunityKpiDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Aggregate queries (GROUP BY / COUNT / SUM ... CASE) — no opportunity rows are loaded. */
@Repository
public class OpportunitySummaryQuery {

    /** Per-stage count and total deal value. */
    public record StageTotals(long count, BigDecimal totalValue) {}

    @PersistenceContext
    private EntityManager entityManager;

    public Map<OpportunityStage, StageTotals> totalsByStage(Long tenantId, DataScope scope) {
        String jpql = "select o.stage, count(o), coalesce(sum(o.dealValue), 0)"
                + " from Opportunity o where o.tenant.id = :tenantId" + ScopedJpql.ownerClause(scope, "o")
                + " group by o.stage";
        List<Object[]> rows = ScopedJpql.bindOwners(entityManager.createQuery(jpql, Object[].class), scope)
                .setParameter("tenantId", tenantId)
                .getResultList();

        Map<OpportunityStage, StageTotals> totals = new EnumMap<>(OpportunityStage.class);
        for (Object[] row : rows) {
            totals.put((OpportunityStage) row[0],
                    new StageTotals(((Number) row[1]).longValue(), toBigDecimal(row[2])));
        }
        return totals;
    }

    public OpportunityKpiDto kpis(Long tenantId, DataScope scope, LocalDate weekStart, LocalDate weekEnd,
                                  LocalDateTime monthStart, LocalDateTime quarterStart) {
        String open = "o.stage <> :won and o.stage <> :lost";
        String jpql = "select new com.company.crm.sales.dto.response.OpportunityKpiDto("
                + " count(case when " + open + " then 1 end),"
                + " coalesce(sum(case when " + open + " then coalesce(o.dealValue, 0) end), 0),"
                + " count(case when " + open + " and o.expectedClosingDate between :weekStart and :weekEnd then 1 end),"
                + " count(case when o.stage = :won and o.stageChangedAt >= :monthStart then 1 end),"
                + " coalesce(sum(case when o.stage = :won and o.stageChangedAt >= :monthStart then coalesce(o.dealValue, 0) end), 0),"
                + " coalesce(sum(case when o.stage = :won and o.stageChangedAt >= :quarterStart then coalesce(o.dealValue, 0) end), 0))"
                + " from Opportunity o where o.tenant.id = :tenantId" + ScopedJpql.ownerClause(scope, "o");
        return ScopedJpql.bindOwners(entityManager.createQuery(jpql, OpportunityKpiDto.class), scope)
                .setParameter("tenantId", tenantId)
                .setParameter("won", OpportunityStage.WON)
                .setParameter("lost", OpportunityStage.LOST)
                .setParameter("weekStart", weekStart)
                .setParameter("weekEnd", weekEnd)
                .setParameter("monthStart", monthStart)
                .setParameter("quarterStart", quarterStart)
                .getSingleResult();
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
