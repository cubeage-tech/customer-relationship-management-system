package com.company.crm.quotation.repository;

import com.company.crm.common.enums.DiscountApprovalStatus;
import com.company.crm.common.enums.QuotationStatus;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.ScopedJpql;
import com.company.crm.quotation.dto.response.QuotationSummaryDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Two aggregate queries: status counts over quotations, and approved-discount money over their
 * line items (totals are derived from line items, exactly as Quotation#getGrandTotal does).
 */
@Repository
public class QuotationSummaryQuery {

    @PersistenceContext
    private EntityManager entityManager;

    public QuotationSummaryDto summarize(Long tenantId, DataScope scope) {
        String owner = ScopedJpql.ownerClause(scope, "q");

        Object[] counts = ScopedJpql.bindOwners(entityManager.createQuery(
                        "select count(case when q.status = :draft then 1 end),"
                                + " count(case when q.status = :sent or q.status = :viewed then 1 end),"
                                + " count(case when q.discountApprovalStatus = :awaiting then 1 end),"
                                + " count(case when q.discountApprovalStatus = :approved then 1 end)"
                                + " from Quotation q where q.tenant.id = :tenantId" + owner, Object[].class), scope)
                .setParameter("tenantId", tenantId)
                .setParameter("draft", QuotationStatus.DRAFT)
                .setParameter("sent", QuotationStatus.PENDING)
                .setParameter("viewed", QuotationStatus.VIEWED)
                .setParameter("awaiting", DiscountApprovalStatus.PENDING)
                .setParameter("approved", DiscountApprovalStatus.APPROVED)
                .getSingleResult();

        Object[] money = ScopedJpql.bindOwners(entityManager.createQuery(
                        "select coalesce(sum(li.quantity * li.unitPrice), 0),"
                                + " coalesce(sum(li.quantity * li.unitPrice * (100 - li.discountPercent) / 100), 0)"
                                + " from Quotation q join q.lineItems li"
                                + " where q.tenant.id = :tenantId and q.discountApprovalStatus = :approved" + owner, Object[].class), scope)
                .setParameter("tenantId", tenantId)
                .setParameter("approved", DiscountApprovalStatus.APPROVED)
                .getSingleResult();

        BigDecimal gross = toBigDecimal(money[0]);
        BigDecimal net = toBigDecimal(money[1]).setScale(2, RoundingMode.HALF_UP);
        BigDecimal averageDiscount = gross.signum() == 0 ? null
                : gross.subtract(net).multiply(BigDecimal.valueOf(100)).divide(gross, 2, RoundingMode.HALF_UP);

        return new QuotationSummaryDto(
                ((Number) counts[0]).longValue(),
                ((Number) counts[1]).longValue(),
                ((Number) counts[2]).longValue(),
                ((Number) counts[3]).longValue(),
                net,
                averageDiscount);
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
