import { apiGet } from './api.service';

export const listTenants = () => apiGet('/api/tenants');
