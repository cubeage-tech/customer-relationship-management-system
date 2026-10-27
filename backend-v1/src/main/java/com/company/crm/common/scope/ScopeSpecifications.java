package com.company.crm.common.scope;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

/**
 * NFR-7.1b: the filters shared by the list endpoints, applied in the database. Tenant and data scope
 * come first; every other filter is optional (null = not applied).
 */
public final class ScopeSpecifications {

    private ScopeSpecifications() {
    }

    public static <T> Specification<T> inTenant(Long tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenant").get("id"), tenantId);
    }

    /** Records the data scope allows: all of the tenant's, or those owned by the scope's users. */
    public static <T> Specification<T> ownedWithin(DataScope scope) {
        if (scope.isTenantWide()) {
            return null;
        }
        return (root, query, cb) -> ownerIn(root, cb, scope.ownerIds());
    }

    static <T> Predicate ownerIn(Root<T> root, CriteriaBuilder cb, Collection<Long> ownerIds) {
        return ownerIds.isEmpty() ? cb.disjunction() : root.get("owner").get("id").in(ownerIds);
    }

    public static <T> Specification<T> ownedBy(Long ownerId) {
        return ownerId == null ? null : (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    /**
     * {@code value} matched against an enum by its database value (e.g. "new_lead"). An unknown
     * value matches nothing, as the in-memory filters did.
     */
    public static <T, E extends Enum<E>> Specification<T> hasEnum(String attribute, String value, Class<E> type, Function<E, String> dbValue) {
        if (value == null || value.isBlank()) {
            return null;
        }
        E match = Arrays.stream(type.getEnumConstants()).filter(e -> dbValue.apply(e).equals(value)).findFirst().orElse(null);
        return (root, query, cb) -> match == null ? cb.disjunction() : cb.equal(root.get(attribute), match);
    }

    public static <T> Specification<T> hasValue(String attribute, Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    /** Case-insensitive "contains" on one text attribute. */
    public static <T> Specification<T> contains(String attribute, String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = containsPattern(search);
        return (root, query, cb) -> cb.like(cb.lower(root.get(attribute)), pattern, '\\');
    }

    /** A LIKE pattern for a case-insensitive "contains", with the wildcards in the input escaped. */
    public static String containsPattern(String search) {
        return "%" + search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
