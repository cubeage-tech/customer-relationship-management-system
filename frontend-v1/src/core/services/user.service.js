import { apiGet, apiPost } from './api.service';

export const listUsers = () => apiGet('/api/users');

export const createUser = (payload) => apiPost('/api/users', payload);
