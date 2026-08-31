import ApiService from './api.service';

export const listOpportunities = (params) => ApiService.getOpportunities(params);

export const getOpportunitySummary = () => ApiService.getOpportunitySummary();

export const getOpportunity = (id) => ApiService.getOpportunity(id);

export const createOpportunity = (data) => ApiService.createOpportunity(data);

export const updateOpportunity = (id, data) => ApiService.updateOpportunity(id, data);

export const deleteOpportunity = (id) => ApiService.deleteOpportunity(id);

export const changeOpportunityStage = (id, stage, lossReason) =>
  ApiService.changeOpportunityStage(id, { stage, lossReason });
