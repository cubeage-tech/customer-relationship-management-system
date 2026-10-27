import ApiService from './api.service';

/** API-G11: whether chat / writing / sentiment are available ({ llmConfigured }). */
export const getAiStatus = () => ApiService.getAiStatus();

/** FR-AI-1: lead scores, best first ({ leadId?, minScore? }). */
export const getLeadScores = (params) => ApiService.getLeadScores(params);

/** FR-AI-2: forecast for period = this_month | next_month | this_quarter | next_quarter. */
export const getForecast = (period) => ApiService.getForecast({ period });

/** FR-AI-4: ask the CRM a question. */
export const askAi = (question) => ApiService.askAi(question);

/** FR-AI-5: draft a message ({ customerId | leadId | opportunityId, channel, tone, purpose, notes }). */
export const generateMessage = (data) => ApiService.generateMessage(data);

/** FR-AI-6: sentiment of { text } or { communicationId }. */
export const analyseSentiment = (data) => ApiService.analyseSentiment(data);

/** NAV-2: an AI first draft of a quotation ({ customerId, opportunityId?, notes? }) → { lineItems, coverNote, checks }. */
export const draftQuotation = (data) => ApiService.draftQuotation(data);

/** SEC-5/SEC-6 (admin): { settings: { aiEnabled, messageContent }, policy }. */
export const getAiSettings = () => ApiService.getAiSettings();

export const saveAiSettings = (data) => ApiService.saveAiSettings(data);

/** SEC-6: whether a customer / lead is excluded from AI ({ excluded }). */
export const getAiExclusion = (recordType, recordId) => ApiService.getAiExclusion(recordType, recordId);

export const setAiExclusion = (recordType, recordId, excluded) => ApiService.setAiExclusion(recordType, recordId, excluded);
