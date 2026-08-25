// api.service.js
import ApiInterceptor from "./interceptor.service";
import ServerUrl from "../constants/serverUrl.constant";

class ApiService {
  // interceptor.service.js already handles auth headers, token expiry,
  // and 401 redirect — ApiService just consumes the configured instance.
  static axiosInstance = ApiInterceptor.init();

  // ------------------ Auth APIs ------------------
  static login(email, password) {
    return this.apipost(ServerUrl.AUTH_LOGIN, { email, password });
  }

  static signup(data) {
    return this.apipost(ServerUrl.AUTH_REGISTER, data);
  }

  static logout() {
    return this.apipost(ServerUrl.AUTH_LOGOUT);
  }

  static verifyEmail(data) {
    return this.apipost(ServerUrl.AUTH_VERIFY_EMAIL, data);
  }

  static forgotPassword(data) {
    return this.apipost(ServerUrl.AUTH_FORGOT_PASSWORD, data);
  }

  static resetPassword(data) {
    return this.apipost(ServerUrl.AUTH_RESET_PASSWORD, data);
  }

  // ------------------ User APIs ------------------
  static getUsers(params) {
    return this.apiget(ServerUrl.USERS, params);
  }

  static createUser(data) {
    return this.apipost(ServerUrl.USERS, data);
  }

  // ------------------ Tenant APIs ------------------
  static getTenants(params) {
    return this.apiget(ServerUrl.TENANTS, params);
  }

  static deactivateTenant(tenantId) {
    return this.apipatch(`${ServerUrl.TENANTS}/${tenantId}/deactivate`);
  }

  static restoreTenant(tenantId) {
    return this.apipatch(`${ServerUrl.TENANTS}/${tenantId}/restore`);
  }

  // ------------------ Plan APIs ------------------
  static listPlanPrices(params) {
    return this.apiget(ServerUrl.PLAN_PRICES, params);
  }

  static updatePlanPrice(plan, data) {
    return this.apiput(ServerUrl.planPrice(plan), data);
  }

  // ------------------ Super Admin APIs ------------------
  static getAllTenantsSuperAdmin(params) {
    return this.apiget(ServerUrl.ADMIN_TENANT, params);
  }

  static getSuperAdminDashboard() {
    return this.apiget(ServerUrl.ADMIN_DASHBOARD);
  }

  // ------------------ Generic Methods ------------------
  static apiget(url, params = {}) {
    return this.axiosInstance.get(url, { params }).then((res) => res.data?.data);
  }

  static apipost(url, body) {
    return this.axiosInstance.post(url, body).then((res) => res.data?.data);
  }

  static apiput(url, body) {
    return this.axiosInstance.put(url, body).then((res) => res.data?.data);
  }

  static apipatch(url, body) {
    return this.axiosInstance.patch(url, body).then((res) => res.data?.data);
  }

  static apidelete(url) {
    return this.axiosInstance.delete(url).then((res) => res.data?.data);
  }
}

export default ApiService;