import ApiService from './api.service';

export const listTenants = () => ApiService.getTenants();

export const deactivateTenant = (tenantId) => ApiService.deactivateTenant(tenantId);

export const restoreTenant = (tenantId) => ApiService.restoreTenant(tenantId);
