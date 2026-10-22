package com.company.crm.lead.dto.response;

/** FR-2.3: one auto-assignment rule; matchSource / matchIndustry null mean "any". */
public record LeadAssignmentRuleResDto(Long id, String name, int priority, boolean active,
                                       String matchSource, String matchIndustry,
                                       Long assignUserId, String assignUserName,
                                       Long assignTeamId, String assignTeamName) {}
