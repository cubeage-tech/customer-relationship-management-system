import ApiService from './api.service';

export const listCampaigns = (params) => ApiService.getCampaigns(params);

export const getCampaignSummary = () => ApiService.getCampaignSummary();

export const getCampaign = (id) => ApiService.getCampaign(id);

export const getCampaignLeads = (id) => ApiService.getCampaignLeads(id);

export const createCampaign = (data) => ApiService.createCampaign(data);

export const updateCampaign = (id, data) => ApiService.updateCampaign(id, data);

export const deleteCampaign = (id) => ApiService.deleteCampaign(id);

export const changeCampaignStatus = (id, status) =>
  ApiService.changeCampaignStatus(id, { status });
