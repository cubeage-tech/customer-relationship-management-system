package com.company.crm.support.repository;

import com.company.crm.common.enums.TicketPriority;
import com.company.crm.common.enums.TicketStatus;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.service.TicketScope;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/** Composable, DB-level filters for service tickets. Tenant is always applied by the caller first. */
public final class ServiceTicketSpecifications {

    private ServiceTicketSpecifications() {
    }

    public static Specification<ServiceTicket> inTenant(Long tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenant").get("id"), tenantId);
    }

    /** Agent visibility: their own tickets, the unassigned queue, or both. */
    public static Specification<ServiceTicket> forAgent(Long agentId, TicketScope scope) {
        return (root, query, cb) -> {
            var technician = root.join("assignedTechnician", JoinType.LEFT);
            var mine = cb.equal(technician.get("id"), agentId);
            var unassigned = cb.isNull(root.get("assignedTechnician"));
            return switch (scope) {
                case MINE -> mine;
                case QUEUE -> unassigned;
                case ALL -> cb.or(mine, unassigned);
            };
        };
    }

    public static Specification<ServiceTicket> hasStatus(TicketStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ServiceTicket> hasPriority(TicketPriority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<ServiceTicket> forCustomer(Long customerId) {
        return customerId == null ? null : (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    /** Case-insensitive match on subject or customer name (same semantics as the original in-memory filter). */
    public static Specification<ServiceTicket> matches(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> {
            var customer = root.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("subject")), pattern, '\\'),
                    cb.like(cb.lower(customer.get("companyName")), pattern, '\\'));
        };
    }
}
