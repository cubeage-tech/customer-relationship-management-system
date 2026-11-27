import ApiService from './api.service';

export const listOpportunities = (params) => ApiService.getOpportunities(params);

export const getOpportunitySummary = () => ApiService.getOpportunitySummary();

/** FR-3.5: open/won/lost value per salesperson and per team, within the caller's scope. */
export const getSalesPerformance = () => ApiService.getSalesPerformance();

export const getOpportunity = (id) => ApiService.getOpportunity(id);

export const createOpportunity = (data) => ApiService.createOpportunity(data);

export const updateOpportunity = (id, data) => ApiService.updateOpportunity(id, data);

export const deleteOpportunity = (id) => ApiService.deleteOpportunity(id);

/** VAL-7: pass `override` ({ reason }) to skip stages; the reason goes to the audit log. */
export const changeOpportunityStage = (id, stage, lossReason, override) =>
  ApiService.changeOpportunityStage(id, { stage, lossReason, force: Boolean(override), overrideReason: override?.reason });
