package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.CustomerSegment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-7.2: evaluates a segment's criteria in the database — the tenant's active customers matching
 * every criterion that is set. A purchase is a won opportunity or an approved quotation.
 */
@Repository
public class CustomerSegmentQuery {

    private static final String PURCHASED_SINCE = "(EXISTS (SELECT 1 FROM opportunities o WHERE o.customer_id = c.id AND o.stage = 'won'"
            + " AND o.stage_changed_at >= :%1$s) OR EXISTS (SELECT 1 FROM quotations q WHERE q.customer_id = c.id"
            + " AND q.status = 'approved' AND q.customer_approved_at >= :%1$s))";

    private static final String LIFETIME_VALUE = "(COALESCE((SELECT SUM(o.deal_value) FROM opportunities o WHERE o.customer_id = c.id"
            + " AND o.stage = 'won'), 0) + COALESCE((SELECT SUM(li.quantity * li.unit_price * (1 - li.discount_percent / 100))"
            + " FROM quotation_line_items li JOIN quotations q ON q.id = li.quotation_id"
            + " WHERE q.customer_id = c.id AND q.status = 'approved'), 0))";

    private static final String CONTACTED_SINCE = "EXISTS (SELECT 1 FROM communication_logs m WHERE m.customer_id = c.id"
            + " AND m.occurred_at >= :%s)";

    @PersistenceContext
    private EntityManager entityManager;

    /** Ids of the matching customers, by company name. */
    @SuppressWarnings("unchecked")
    public List<Long> customerIds(CustomerSegment s, LocalDateTime now) {
        StringBuilder sql = new StringBuilder("SELECT c.id FROM customers c WHERE c.tenant_id = :tenantId AND c.status = 'active'");
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", s.getTenant().getId());

        List<String> industries = industries(s.getIndustries());
        if (!industries.isEmpty()) {
            sql.append(" AND c.industry IN (:industries)");
            params.put("industries", industries);
        }
        if (s.getLeadSource() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM leads l WHERE l.converted_customer_id = c.id AND l.source = :leadSource)");
            params.put("leadSource", s.getLeadSource());
        }
        if (s.getPurchasedWithinDays() != null) {
            sql.append(" AND ").append(PURCHASED_SINCE.formatted("boughtSince"));
            params.put("boughtSince", now.minusDays(s.getPurchasedWithinDays()));
        }
        if (s.getNotPurchasedWithinDays() != null) {
            sql.append(" AND NOT ").append(PURCHASED_SINCE.formatted("notBoughtSince"));
            params.put("notBoughtSince", now.minusDays(s.getNotPurchasedWithinDays()));
        }
        if (s.getMinPurchaseValue() != null) {
            sql.append(" AND ").append(LIFETIME_VALUE).append(" >= :minValue");
            params.put("minValue", s.getMinPurchaseValue());
        }
        if (s.getContactedWithinDays() != null) {
            sql.append(" AND ").append(CONTACTED_SINCE.formatted("contactedSince"));
            params.put("contactedSince", now.minusDays(s.getContactedWithinDays()));
        }
        if (s.getNotContactedWithinDays() != null) {
            sql.append(" AND NOT ").append(CONTACTED_SINCE.formatted("notContactedSince"));
            params.put("notContactedSince", now.minusDays(s.getNotContactedWithinDays()));
        }
        sql.append(" ORDER BY c.company_name, c.id");

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return ((List<Number>) query.getResultList()).stream().map(Number::longValue).toList();
    }

    public static List<String> industries(String csv) {
        return csv == null || csv.isBlank() ? List.of()
                : Arrays.stream(csv.split(",")).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }
}
