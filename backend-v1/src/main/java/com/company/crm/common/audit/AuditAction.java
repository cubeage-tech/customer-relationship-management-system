package com.company.crm.common.audit;

/** Business actions recorded in the audit trail. Stored by name; never rename an existing constant. */
public enum AuditAction {
    TICKET_CLAIMED,
    DISCOUNT_APPROVED,
    DISCOUNT_REJECTED
}
