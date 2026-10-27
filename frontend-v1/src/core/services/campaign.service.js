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

/** FR-7.1c: launch now (email/SMS with a segment start sending). */
export const launchCampaign = (id) => ApiService.launchCampaign(id);

/** FR-7.1b: launch automatically at scheduledAt ("YYYY-MM-DDTHH:mm"). */
export const scheduleCampaign = (id, scheduledAt) => ApiService.scheduleCampaign(id, scheduledAt);

/** FR-7.4: sent / opened / clicked / converted. */
export const getCampaignMetrics = (id) => ApiService.getCampaignMetrics(id);

export const getCampaignRecipients = (id) => ApiService.getCampaignRecipients(id);

/** FR-7.5: segments ranked by how likely they are to respond on a channel. */
export const getSegmentRecommendations = (channel) => ApiService.getSegmentRecommendations(channel);

/** FR-7.6: predicted performance for a channel + segment (+ budget), before saving. */
export const predictCampaign = (params) => ApiService.predictCampaign(params);

/** FR-7.6: a saved campaign's prediction; once launched, with the actual results. */
export const getCampaignPrediction = (id) => ApiService.getCampaignPrediction(id);

/** FR-7.7: current offers ranked for one customer, with reasons. */
export const getOfferRecommendations = (customerId) => ApiService.getOfferRecommendations(customerId);
