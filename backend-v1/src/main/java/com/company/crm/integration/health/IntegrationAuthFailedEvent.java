package com.company.crm.integration.health;

/** INT-8: an outside service rejected the CRM's credentials (published once, then daily while it lasts). */
public record IntegrationAuthFailedEvent(String integration, String detail) {}
