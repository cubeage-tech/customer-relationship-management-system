import { apiGet, apiPatch } from './api.service';

export const listTenants = () => apiGet('/api/tenants');

export const deactivateTenant = (tenantId) =>
	apiPatch(`/api/tenants/${tenantId}/deactivate`);

export const restoreTenant = (tenantId) =>
	apiPatch(`/api/tenants/${tenantId}/restore`);
