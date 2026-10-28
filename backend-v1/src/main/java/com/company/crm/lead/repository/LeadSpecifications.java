package com.company.crm.lead.repository;

import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.ScopeSpecifications;
import com.company.crm.lead.entity.Lead;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/** NFR-7.1b: lead list filters, in the database (same semantics as the original in-memory ones). */
public final class LeadSpecifications {

    private LeadSpecifications() {
    }

    /** The data scope, plus (FR-2.3) leads of the teams the caller manages. */
    public static Specification<Lead> visible(DataScope scope, List<Long> managedTeamIds) {
        if (scope.isTenantWide()) {
            return null;
        }
        return (root, query, cb) -> {
            var owned = scope.ownerIds().isEmpty() ? cb.disjunction() : root.get("owner").get("id").in(scope.ownerIds());
            if (managedTeamIds.isEmpty()) {
                return owned;
            }
            return cb.or(owned, root.join("team", JoinType.LEFT).get("id").in(managedTeamIds));
        };
    }

    /** FR-AI-1.3: leads scored at least {@code minScore}. */
    public static Specification<Lead> scoreAtLeast(Integer minScore) {
        return minScore == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Integer>get("aiScore"), minScore);
    }

    /** Case-insensitive match on the lead's name or company. */
    public static Specification<Lead> matches(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = ScopeSpecifications.containsPattern(search);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("leadName")), pattern, '\\'),
                cb.like(cb.lower(root.get("companyName")), pattern, '\\'));
    }
}
