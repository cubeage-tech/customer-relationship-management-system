import axios from "axios";
import StorageService from "./storage.service";
import { APPLICATION_CONSTANTS } from "../constants/app.constant";

/**
 * Resolve the API base URL from environment variables.
 *
 * Single source of truth: VITE_API_URL.
 * VITE_REACT_APP_API_URL is kept as a legacy alias so existing deployments
 * that set only that variable continue to work without a redeploy.
 *
 * Fails loudly at startup in production if neither is set — a blank screen
 * caused by a missing env var is much harder to debug than an explicit error.
 */
function resolveBaseURL() {
  const url =
    import.meta.env.VITE_API_URL ||
    import.meta.env.VITE_REACT_APP_API_URL;

  if (!url) {
    const msg =
      "[API] VITE_API_URL is not set. " +
      "Add it to your .env file (dev) or deployment environment variables (prod). " +
      "Example: VITE_API_URL=https://your-backend.onrender.com/api";

    // In production a missing URL means every API call will fail — surface it immediately.
    if (import.meta.env.PROD) {
      throw new Error(msg);
    }

    // In development fall back to localhost so the dev server still starts.
    console.error(msg);
    return "http://localhost:3000/api";
  }

  return url;
}

/**
 * Decode JWT payload without verifying signature (client-side only).
 * Used to check expiry before making requests.
 */
function decodeJwtPayload(token) {
  try {
    const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(base64));
  } catch {
    return null;
  }
}

function isTokenExpired(token) {
  const payload = decodeJwtPayload(token);
  if (!payload?.exp) return false;
  // 60s buffer — accounts for clock skew between client and server
  return Date.now() / 1000 > payload.exp - 60;
}

function clearAuthAndRedirect() {
  // Use constants for storage keys — never raw string literals
  StorageService.removeData(APPLICATION_CONSTANTS.STORAGE.TOKEN);
  StorageService.removeData(APPLICATION_CONSTANTS.STORAGE.USER_DETAILS);
  // Only redirect if not already on an auth page
  if (!window.location.pathname.includes("/login")) {
    window.location.href = "/login";
  }
}

class ApiInterceptor {
  static axiosReference = axios.create({
    baseURL: resolveBaseURL(),
    timeout: 30000, // 30s request timeout
  });

  static initialized = false;

  static init() {
    if (this.initialized) return this.axiosReference;

    // ── Request interceptor ────────────────────────────────────────────────
    this.axiosReference.interceptors.request.use((config) => {
      const token = StorageService.getData(APPLICATION_CONSTANTS.STORAGE.TOKEN);

      if (token) {
        // Check expiry before sending — avoids a round-trip for expired tokens
        if (isTokenExpired(token)) {
          clearAuthAndRedirect();
          return Promise.reject(new Error("Session expired. Please log in again."));
        }
        config.headers.Authorization = `Bearer ${token}`;
      }

      return config;
    });

    // ── Response interceptor ───────────────────────────────────────────────
    this.axiosReference.interceptors.response.use(
      (res) => res,
      (err) => {
        if (err.response?.status === 401) {
          clearAuthAndRedirect();
        }

        // Network error — no response from server
        if (!err.response) {
          console.error("[API] Network error:", err.message);
        }

        return Promise.reject(err);
      }
    );

    this.initialized = true;
    return this.axiosReference;
  }
}

export default ApiInterceptor;
