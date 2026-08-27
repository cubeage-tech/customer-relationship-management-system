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
      "";

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

/**
 * Auth endpoints must never trigger a session wipe. A 401 from /auth/login means
 * "wrong credentials", not "your session expired" — treating the two the same
 * turned a failed sign-in into a full storage clear and a page reload.
 */
const AUTH_PATHS = [
  "/auth/login",
  "/auth/signup",
  "/auth/verify-email",
  "/auth/forgot-password",
  "/auth/reset-password",
];

function isAuthEndpoint(url = "") {
  return AUTH_PATHS.some((path) => url.includes(path));
}

/**
 * Did we actually send credentials on this request? Axios v1 stores headers in an
 * AxiosHeaders instance, so read through .get() when it's available.
 */
function hadAuthHeader(config) {
  const headers = config?.headers;
  if (!headers) return false;
  if (typeof headers.get === "function") return Boolean(headers.get("Authorization"));
  return Boolean(headers.Authorization || headers.authorization);
}

// Several requests can 401 at once (a dashboard fans out on mount). Without this
// latch each one fires its own navigation and the reloads stack up.
let redirecting = false;

function clearAuthAndRedirect() {
  if (redirecting) return;
  redirecting = true;

  // Use constants for storage keys — never raw string literals
  StorageService.removeData(APPLICATION_CONSTANTS.STORAGE.TOKEN);
  StorageService.removeData(APPLICATION_CONSTANTS.STORAGE.USER_DETAILS);

  // Only redirect if not already on an auth page
  if (!window.location.pathname.includes("/login")) {
    window.location.replace("/login");
  } else {
    redirecting = false;
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
      // Never attach (or validate) a token on the auth endpoints. A stale token
      // left in storage used to make the request interceptor reject the very
      // login call that would have replaced it.
      if (isAuthEndpoint(config.url)) return config;

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
        // Only a 401 on a request we actually authenticated means the session is
        // dead. A 401 on /auth/login (bad password) or on a call that carried no
        // token at all is not a reason to sign the user out — that is what made a
        // single misrouted request log you straight back out after signing in.
        if (
          err.response?.status === 401 &&
          !isAuthEndpoint(err.config?.url) &&
          hadAuthHeader(err.config)
        ) {
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