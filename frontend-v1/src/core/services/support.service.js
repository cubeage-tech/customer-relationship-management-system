import ApiService from './api.service';

/** FR-6.1: a customer's assets (equipment/products tickets can reference). */
export const listCustomerAssets = (customerId) => ApiService.getCustomerAssets(customerId);
export const createCustomerAsset = (customerId, data) => ApiService.createCustomerAsset(customerId, data);
export const updateCustomerAsset = (customerId, assetId, data) => ApiService.updateCustomerAsset(customerId, assetId, data);
export const deleteCustomerAsset = (customerId, assetId) => ApiService.deleteCustomerAsset(customerId, assetId);

/** FR-6.3b: ticket auto-assignment rules (admin). */
export const listTicketAssignmentRules = () => ApiService.getTicketAssignmentRules();
export const createTicketAssignmentRule = (data) => ApiService.createTicketAssignmentRule(data);
export const updateTicketAssignmentRule = (id, data) => ApiService.updateTicketAssignmentRule(id, data);
export const deleteTicketAssignmentRule = (id) => ApiService.deleteTicketAssignmentRule(id);

/** FR-6.1: the admin's shareable customer support form link ({ url }). */
export const getSupportFormLink = () => ApiService.getSupportFormLink();
export const resetSupportFormLink = () => ApiService.resetSupportFormLink();

/** FR-6.1 / FR-6.5: public pages opened by customers from a link (no login). */
export const getPublicSupportForm = (token) => ApiService.getPublicSupportForm(token);
export const submitPublicSupportRequest = (token, data) => ApiService.submitPublicSupportRequest(token, data);
export const getPublicFeedback = (token) => ApiService.getPublicFeedback(token);
export const submitPublicFeedback = (token, data) => ApiService.submitPublicFeedback(token, data);
