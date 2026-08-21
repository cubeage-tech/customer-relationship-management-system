import { apiGet } from './api.service';

export const getSuperAdminDashboard = () => apiGet('/api/dashboard/super-admin');