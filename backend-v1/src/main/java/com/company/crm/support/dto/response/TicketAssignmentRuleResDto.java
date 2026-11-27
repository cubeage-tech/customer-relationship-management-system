package com.company.crm.support.dto.response;

/** FR-6.3b: one ticket auto-assignment rule; null match fields mean "any", null assignUserId = round-robin. */
public record TicketAssignmentRuleResDto(Long id, String name, int priority, boolean active,
                                         String matchPriority, String matchIndustry,
                                         Long assignUserId, String assignUserName) {}
