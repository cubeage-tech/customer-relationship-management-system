import ApiService from './api.service';

/** API-G11: whether chat / writing / sentiment are available ({ llmConfigured }). */
export const getAiStatus = () => ApiService.getAiStatus();

/** FR-AI-1: lead scores, best first ({ leadId?, minScore? }). */
export const getLeadScores = (params) => ApiService.getLeadScores(params);

/** FR-AI-2: forecast for period = this_month | next_month | this_quarter | next_quarter. */
export const getForecast = (period) => ApiService.getForecast({ period });

/** FR-AI-4: ask the CRM a question. */
/** FR-AI-4: a question, with up to five earlier questions of the conversation for context. */
export const askAi = (question, history) => ApiService.askAi(question, history);

/** FR-AI-3.2: best times to contact { customerId } or { leadId }. */
export const getContactTime = (params) => ApiService.getContactTime(params);

/** FR-AI-3.3: talking points for { customerId } | { leadId } | { opportunityId }. */
export const getTalkingPoints = (data) => ApiService.getTalkingPoints(data);

/** FR-AI-5: draft a message ({ customerId | leadId | opportunityId, channel, tone, purpose, notes }). */
export const generateMessage = (data) => ApiService.generateMessage(data);

/** FR-AI-5.3: campaign message ({ campaignId, segmentId?, purpose, tone?, notes? }) → { subject, body, basedOn }. */
export const draftCampaignCopy = (data) => ApiService.draftCampaignCopy(data);

/** FR-AI-5.3: proposal text for a deal ({ opportunityId, quotationId?, tone?, notes? }) → { title, sections, text, basedOn }. */
export const draftProposal = (data) => ApiService.draftProposal(data);

/** FR-AI-6: sentiment of { text }, { communicationId } or { ticketId } (its feedback); the last two are stored (FR-AI-6.1). */
export const analyseSentiment = (data) => ApiService.analyseSentiment(data);

/** NAV-2: an AI first draft of a quotation ({ customerId, opportunityId?, notes? }) → { lineItems, coverNote, checks }. */
export const draftQuotation = (data) => ApiService.draftQuotation(data);

/** SEC-5/SEC-6 (admin): { settings: { aiEnabled, messageContent, autoSentiment }, policy }. */
export const getAiSettings = () => ApiService.getAiSettings();

export const saveAiSettings = (data) => ApiService.saveAiSettings(data);

/** SEC-6: whether a customer / lead is excluded from AI ({ excluded }). */
export const getAiExclusion = (recordType, recordId) => ApiService.getAiExclusion(recordType, recordId);

export const setAiExclusion = (recordType, recordId, excluded) => ApiService.setAiExclusion(recordType, recordId, excluded);

/** FR-4.7: { customerId, productNames } → per product the recommended discount, net price, win rate and why. */
export const getPriceRecommendations = (data) => ApiService.getPriceRecommendations(data);

/** NFR-7.1c: each AI feature's p50/p95 response time against its budget (admins). */
export const getAiPerformance = () => ApiService.getAiPerformance();

/** FR-AI-2.2: close chance of each open deal ({ opportunityId } for one), with the reasons. */
export const getDealProbabilities = (params) => ApiService.getDealProbabilities(params);

/** FR-AI-3.1: products to offer a customer next, from purchase history. */
export const getProductRecommendations = (customerId) => ApiService.getProductRecommendations(customerId);

/** FR-AI-6.2: a customer's churn risk now → { level, score, factors, assessedAt }. */
export const getChurnRisk = (customerId) => ApiService.getChurnRisk(customerId);

/** FR-AI-6.2: customers I can see at level (medium | high) or above, riskiest first. */
export const getChurnRisks = (level) => ApiService.getChurnRisks(level);

/** FR-AI-6.3: a customer's sentiment month by month over `months` (3–24) → { series, direction, summary, … }. */
/** FR-AI-8.1: each salesperson's activity and conversion ({ from, to, userId? }) vs the team median and the previous period. */
/** FR-AI-8.2: coaching for { userId?, from?, to? } → { summary, focusAreas, strengthsToKeep, weeklyGoals, source }. */
/** FR-AI-8.3: coaching shared with a salesperson (me by default), newest first. */
export const listSharedCoaching = (userId) => ApiService.listSharedCoaching(userId);

/** FR-AI-8.3: share a plan ({ userId, from, to, summary, focusAreas, strengthsToKeep, weeklyGoals, source, note }). */
export const shareCoaching = (data) => ApiService.shareCoaching(data);

export const acknowledgeCoaching = (id, reply) => ApiService.acknowledgeCoaching(id, reply);

export const getSalesCoaching = (params) => ApiService.getSalesCoaching(params);

export const getSalespersonAnalysis = (params) => ApiService.getSalespersonAnalysis(params);

export const getSentimentTrend = (customerId, months) => ApiService.getSentimentTrend(customerId, months);
