package com.company.crm.customer.repository;

import com.company.crm.common.enums.CustomerStatus;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.ScopedJpql;
import com.company.crm.customer.dto.response.CustomerSummaryDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/** One aggregate query (COUNT ... CASE) — no rows are loaded into memory. */
@Repository
public class CustomerSummaryQuery {

    @PersistenceContext
    private EntityManager entityManager;

    public CustomerSummaryDto summarize(Long tenantId, DataScope scope, LocalDateTime quarterStart) {
        String jpql = "select new com.company.crm.customer.dto.response.CustomerSummaryDto("
                + " count(c),"
                + " count(case when c.status = :active then 1 end),"
                + " count(case when c.createdAt >= :quarterStart then 1 end))"
                + " from Customer c where c.tenant.id = :tenantId" + ScopedJpql.ownerClause(scope, "c");
        return ScopedJpql.bindOwners(entityManager.createQuery(jpql, CustomerSummaryDto.class), scope)
                .setParameter("tenantId", tenantId)
                .setParameter("active", CustomerStatus.ACTIVE)
                .setParameter("quarterStart", quarterStart)
                .getSingleResult();
    }
}
