import ApiService from './api.service';

/**
 * NFR-7.2d: audit events, newest first. params = { from, to (YYYY-MM-DD), entityType, action,
 * actorId, entityId, page, size, tenantId (platform admin only) }.
 */
export const getAuditEvents = (params) => ApiService.getAuditEvents(params);

/** NFR-7.6b: SLA hours per priority and the discount-approval threshold. */
export const getTenantSettings = () => ApiService.getTenantSettings();
export const updateTenantSettings = (data) => ApiService.updateTenantSettings(data);
