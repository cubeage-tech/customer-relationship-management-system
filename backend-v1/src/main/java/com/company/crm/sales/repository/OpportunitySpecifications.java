package com.company.crm.sales.repository;

import com.company.crm.common.scope.ScopeSpecifications;
import com.company.crm.sales.entity.Opportunity;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/** NFR-7.1b: opportunity list filters, in the database (same semantics as the original in-memory ones). */
public final class OpportunitySpecifications {

    private OpportunitySpecifications() {
    }

    public static Specification<Opportunity> forCustomer(Long customerId) {
        return customerId == null ? null : (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    /** Case-insensitive match on the customer's name or the product/service. */
    public static Specification<Opportunity> matches(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = ScopeSpecifications.containsPattern(search);
        return (root, query, cb) -> {
            var customer = root.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(customer.get("companyName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("productService")), pattern, '\\'));
        };
    }
}
