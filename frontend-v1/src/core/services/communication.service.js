import ApiService from './api.service';

/** FR-5.6: timeline for a customer ({ customerId }) or a lead ({ leadId }), newest first. */
export const listCommunications = (params) => ApiService.getCommunications(params);

/** FR-5.4: log a call. */
export const logCall = (data) => ApiService.logCall(data);

/** FR-5.1: send an email (logged as sent or failed). */
export const sendEmail = (data) => ApiService.sendCommunicationEmail(data);

/** FR-5.2: send a WhatsApp message. */
export const sendWhatsApp = (data) => ApiService.sendWhatsApp(data);

/** API-G06: send an SMS to a customer or lead (logged on the timeline). */
export const sendSms = (data) => ApiService.sendSms(data);

/** UI-1: unified inbox across the records I can see ({ channel, direction, limit }). */
export const getInbox = (params) => ApiService.getInbox(params);

/** FR-5.3: SMS alert rules for the tenant (admin). */
export const getSmsAlerts = () => ApiService.getSmsAlerts();

export const updateSmsAlerts = (rules) => ApiService.updateSmsAlerts(rules);
