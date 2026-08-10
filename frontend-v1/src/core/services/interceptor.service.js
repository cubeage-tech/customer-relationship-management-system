import axiosInstance from '../utils/axios';
import { getToken, clearSession } from './storage.service';
import RoutePath from '../constants/routes.constant';

export const attachInterceptors = () => {
  axiosInstance.interceptors.request.use((config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  axiosInstance.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401) {
        clearSession();
        window.location.href = RoutePath.LOGIN;
      }
      return Promise.reject(error);
    }
  );
};
