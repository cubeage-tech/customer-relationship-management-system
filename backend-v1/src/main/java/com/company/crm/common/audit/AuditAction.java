package com.company.crm.common.audit;

/** Business actions recorded in the audit trail. Stored by name; never rename an existing constant. */
public enum AuditAction {
    CUSTOMER_CREATED,
    CUSTOMER_UPDATED,
    CUSTOMER_ARCHIVED,
    CUSTOMER_RESTORED,
    CUSTOMER_CONTACT_ADDED,
    CUSTOMER_CONTACT_UPDATED,
    CUSTOMER_CONTACT_REMOVED,
    TEAM_CREATED,
    TEAM_UPDATED,
    TEAM_DELETED,
    TEAM_MEMBERS_CHANGED,
    TICKET_CLAIMED,
    /** FR-6.4c: an at-risk/breached SLA was escalated (system action, no actor). */
    TICKET_SLA_ESCALATED,
    DISCOUNT_APPROVED,
    DISCOUNT_REJECTED
}
