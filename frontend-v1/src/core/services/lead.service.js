import ApiService from './api.service';

export const listLeads = (params) => ApiService.getLeads(params);

export const getLead = (id) => ApiService.getLead(id);

export const createLead = (data) => ApiService.createLead(data);

export const updateLead = (id, data) => ApiService.updateLead(id, data);

export const deleteLead = (id) => ApiService.deleteLead(id);

export const changeLeadStage = (id, stage, force = false) =>
  ApiService.changeLeadStage(id, { stage, force });

export const assignLead = (id, ownerId) => ApiService.assignLead(id, { ownerId });
