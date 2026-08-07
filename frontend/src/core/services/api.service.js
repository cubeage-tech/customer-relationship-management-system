import axiosInstance from '../utils/axios';

export const apiGet = (url, config) => axiosInstance.get(url, config).then((res) => res.data);

export const apiPost = (url, body, config) =>
  axiosInstance.post(url, body, config).then((res) => res.data);

export const apiPut = (url, body, config) =>
  axiosInstance.put(url, body, config).then((res) => res.data);

export const apiDelete = (url, config) =>
  axiosInstance.delete(url, config).then((res) => res.data);
