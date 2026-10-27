import ApiService from './api.service';

/** NOT-5: my notifications ({ unread, items }). */
export const getNotifications = () => ApiService.getNotifications();

export const markNotificationRead = (id) => ApiService.markNotificationRead(id);

export const markAllNotificationsRead = () => ApiService.markAllNotificationsRead();

/** NOT-6 (admin): each event and its channels. */
export const getNotificationSettings = () => ApiService.getNotificationSettings();

/** NOT-6: [{ event, channel, enabled, recipient? }] */
export const saveNotificationSettings = (changes) => ApiService.saveNotificationSettings(changes);

/** NOT-8 (admin): the delivery log ({ channel, status, event, from, to, page }). */
export const getNotificationDeliveries = (params) => ApiService.getNotificationDeliveries(params);

/** NOT-7: a customer's consent ({ marketingEmail, sms, whatsapp, ... }). */
export const getCustomerConsent = (customerId) => ApiService.getCustomerConsent(customerId);

export const saveCustomerConsent = (customerId, data) => ApiService.saveCustomerConsent(customerId, data);

/** INT-8 (platform admin): each integration's credential status. */
export const getIntegrationHealth = () => ApiService.getIntegrationHealth();

export const resolveIntegration = (integration) => ApiService.resolveIntegration(integration);
