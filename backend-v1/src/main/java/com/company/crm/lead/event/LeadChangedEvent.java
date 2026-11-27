package com.company.crm.lead.event;

/** FR-AI-1.2: a lead was created or changed — its AI score is refreshed once the change commits. */
public record LeadChangedEvent(Long tenantId, Long leadId) {}
