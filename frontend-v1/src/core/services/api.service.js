import axiosInstance from '../utils/axios';

// The backend wraps every response as { success, message, data } — unwrap to `data`
// here so callers (and the dev-mode mocks they fall back to) work with the same
// plain payload shape either way.
const unwrap = (res) => res.data?.data;

export const apiGet = (url, config) => axiosInstance.get(url, config).then(unwrap);

export const apiPost = (url, body, config) =>
  axiosInstance.post(url, body, config).then(unwrap);

export const apiPut = (url, body, config) =>
  axiosInstance.put(url, body, config).then(unwrap);

export const apiPatch = (url, body, config) =>
  axiosInstance.patch(url, body, config).then(unwrap);

export const apiDelete = (url, config) =>
  axiosInstance.delete(url, config).then(unwrap);
