package com.company.crm.communication.dto.responce;

/** FR-5.3: one configurable SMS alert event for the tenant. */
public record SmsAlertRuleResDto(String event, boolean enabled, String recipient) {}
