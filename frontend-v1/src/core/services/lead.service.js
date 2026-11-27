import ApiService from './api.service';

export const listLeads = (params) => ApiService.getLeads(params);

export const getLead = (id) => ApiService.getLead(id);

export const createLead = (data) => ApiService.createLead(data);

export const updateLead = (id, data) => ApiService.updateLead(id, data);

export const deleteLead = (id) => ApiService.deleteLead(id);

/** BR-1: skipping stages needs force (an override), with an optional reason for the audit log. */
export const changeLeadStage = (id, stage, force = false, overrideReason) =>
  ApiService.changeLeadStage(id, { stage, force, overrideReason });

export const assignLead = (id, ownerId) => ApiService.assignLead(id, { ownerId });

/** FR-2.3: assign a lead to a sales team; teamId null clears it. */
export const assignLeadTeam = (id, teamId) => ApiService.assignLeadTeam(id, { teamId });

export const listAssignableTeams = () => ApiService.getLeadAssignableTeams();

/** FR-2.3: auto-assignment rules for new leads (admin). */
export const listAssignmentRules = () => ApiService.getLeadAssignmentRules();

export const createAssignmentRule = (data) => ApiService.createLeadAssignmentRule(data);

export const updateAssignmentRule = (id, data) => ApiService.updateLeadAssignmentRule(id, data);

export const deleteAssignmentRule = (id) => ApiService.deleteLeadAssignmentRule(id);
