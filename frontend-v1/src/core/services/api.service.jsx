import ApiInterceptor from "./interceptor.service";
import ServerUrl from "../constants/serverUrl.constant";

class ApiService {
  // Axios instance
  static axiosInstance = ApiInterceptor.init();

    // ==================== AUTH INTERCEPTOR ====================
  static initializeInterceptor() {
    const instance = this.axiosInstance;

    instance.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('token');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    instance.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          console.error(" 401 Unauthorized - Token expired invalid");
        }
        return Promise.reject(error);
      }
    );
  }

  // Auto initialize
  static {
    ApiService.initializeInterceptor();
  }

}

export default ApiService;
