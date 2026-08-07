import { apiPost } from './api.service';

export const login = (credentials) => apiPost('/auth/login', credentials);

export const signup = (payload) => apiPost('/auth/signup', payload);

export const logout = () => apiPost('/auth/logout');
