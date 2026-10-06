package com.company.crm.lead.repository;

import com.company.crm.common.enums.LeadStage;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.ScopedJpql;
import com.company.crm.lead.dto.response.LeadSummaryDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/** One aggregate query (COUNT ... CASE) — no rows are loaded into memory. */
@Repository
public class LeadSummaryQuery {

    @PersistenceContext
    private EntityManager entityManager;

    public LeadSummaryDto summarize(Long tenantId, DataScope scope, LocalDateTime monthStart) {
        String jpql = "select new com.company.crm.lead.dto.response.LeadSummaryDto("
                + " count(l),"
                + " count(case when l.stage <> :converted then 1 end),"
                + " count(case when l.createdAt >= :monthStart then 1 end))"
                + " from Lead l where l.tenant.id = :tenantId" + ScopedJpql.ownerClause(scope, "l");
        return ScopedJpql.bindOwners(entityManager.createQuery(jpql, LeadSummaryDto.class), scope)
                .setParameter("tenantId", tenantId)
                .setParameter("converted", LeadStage.CONVERTED)
                .setParameter("monthStart", monthStart)
                .getSingleResult();
    }
}
