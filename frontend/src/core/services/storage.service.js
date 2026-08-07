import { STORAGE_KEYS } from '../constants/app.constant';

export const getToken = () => localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);

export const setToken = (token) => localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, token);

export const removeToken = () => localStorage.removeItem(STORAGE_KEYS.AUTH_TOKEN);

export const getStoredUser = () => {
  const raw = localStorage.getItem(STORAGE_KEYS.AUTH_USER);
  return raw ? JSON.parse(raw) : null;
};

export const setStoredUser = (user) =>
  localStorage.setItem(STORAGE_KEYS.AUTH_USER, JSON.stringify(user));

export const removeStoredUser = () => localStorage.removeItem(STORAGE_KEYS.AUTH_USER);

export const clearSession = () => {
  removeToken();
  removeStoredUser();
};
