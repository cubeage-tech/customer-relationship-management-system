import ApiService from './api.service';

/** API-G02: what happened with a customer, newest first. */
export const getCustomerHistory = (customerId, types) => ApiService.getCustomerHistory(customerId, types);

/** API-G08: a campaign's performance (metrics, revenue won, cost per lead, return on spend, by day). */
export const getCampaignPerformance = (campaignId) => ApiService.getCampaignPerformance(campaignId);

/** NAV-M11: which integrations are set up, and the ERP API keys. */
export const getIntegrationStatus = () => ApiService.getIntegrationStatus();

export const listIntegrationKeys = () => ApiService.listIntegrationKeys();

/** → { key, apiKey } — the API key itself is shown once. */
export const createIntegrationKey = (name) => ApiService.createIntegrationKey(name);

export const revokeIntegrationKey = (id) => ApiService.revokeIntegrationKey(id);

/** SEC-8: data-protection requests. */
export const listDataRequests = () => ApiService.listDataRequests();

export const createDataRequest = (data) => ApiService.createDataRequest(data);

export const eraseDataRequest = (id, confirm) => ApiService.eraseDataRequest(id, confirm);

export const rejectDataRequest = (id, reason) => ApiService.rejectDataRequest(id, reason);

/** Downloads the request's JSON export. */
export const downloadDataExport = async (id, name) => {
  const response = await ApiService.exportDataRequest(id);
  const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/json' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${(name || 'personal-data').replace(/[^\w-]+/g, '-')}.json`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

/** BIZ-2: industry editions ({ current, editions }). */
export const getEditions = () => ApiService.getEditions();

export const applyEdition = (edition) => ApiService.applyEdition(edition);

/** SEC-1b: { minLength, maxLength, minCharacterKinds, breachCheck, history }. */
export const getPasswordPolicy = () => ApiService.getPasswordPolicy();

/** VAL-1/VAL-3: { type: customer|lead|contact, companyName, email, excludeId } → matches. */
export const checkDuplicates = (data) => ApiService.checkDuplicates(data);

/** INT-9: { lastDay, calls, total, page, size } — filter by provider, outcome, tenantId. */
export const getIntegrationCalls = (params) => ApiService.getIntegrationCalls(params);
