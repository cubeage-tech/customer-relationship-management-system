import axios from 'axios';
import { API_BASE_URL, API_TIMEOUT_MS } from '../constants/serverUrl.constant';

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json',
  },
});

export default axiosInstance;
