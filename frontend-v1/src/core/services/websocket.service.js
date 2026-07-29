import { io } from "socket.io-client";
import StorageService from './storage.service';
import { APPLICATION_CONSTANTS } from '../constants/app.constant';

let socket = null;

/**
 * Get the authentication token from storage using the canonical constant key.
 */
const getToken = () => {
  return StorageService.getData(APPLICATION_CONSTANTS.STORAGE.TOKEN);
};

/**
 * Get the logged-in user's id from storage, for re-joining their personal
 * notification/update room on every connect (see the "connect" handler below).
 */
const getStoredUserId = () => {
  return StorageService.getData(APPLICATION_CONSTANTS.STORAGE.USER_DETAILS)?.id || null;
};

/**
 * Connect to the WebSocket server
 * @returns {socket|null} The socket instance or null if connection fails
 */
export const connectSocket = () => {

  // Reuse any existing socket — whether connected or still connecting.
  // Destroying a connecting socket orphans listeners already registered by
  // other hooks (e.g. useTravelerDashboard registers delivery_verified before
  // useSocketEvents calls connectSocket). Re-login is handled by
  // disconnectSocket(), which sets socket = null before the new login token
  // is issued, so the next connectSocket() call always creates a fresh instance.
  if (socket) {
    return socket;
  }

  try {

    const token = getToken();

    // VITE_WS_URL must point to the server root (no /api suffix).
    // VITE_API_URL is intentionally NOT used as a fallback here because it
    // includes the /api path suffix which breaks Socket.IO connections.
    const wsUrl = import.meta.env.VITE_WS_URL;

    if (!wsUrl) {
      const msg =
        "[WebSocket] VITE_WS_URL is not set. " +
        "Add it to your .env file. Example: VITE_WS_URL=https://your-backend.onrender.com";

      if (import.meta.env.PROD) {
        console.error(msg);
        return null; // Don't attempt connection — it will fail anyway
      }

      console.warn(msg + " — falling back to http://localhost:3000 for development.");
    }

    const resolvedUrl = wsUrl || "http://localhost:3000";

    console.log("🔄 Attempting WebSocket connection to:", resolvedUrl);

    socket = io(resolvedUrl, {

      auth: { token },

      transports: ["websocket", "polling"],

      // Cap reconnection attempts — Infinity causes runaway reconnect loops
      // and burns CPU/battery when the server is down for an extended period.
      reconnection: true,
      reconnectionAttempts: 10,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 30000, // exponential back-off ceiling: 30s

      timeout: 10000,

      autoConnect: true,

    });

    socket.on("connect", () => {
      console.log("✅ WebSocket connected:", socket.id);

      // Re-join the user's personal room on every connect, not just the first
      // one — Socket.IO's automatic reconnection (WiFi blip, laptop sleep)
      // reuses this same "connect" event, but room membership does NOT
      // survive a disconnect server-side, so without this, live notification
      // and order-update delivery would silently stop after any reconnect
      // until a full page refresh.
      const userId = getStoredUserId();
      if (userId) socket.emit("join_user", userId);
    });

    socket.on("disconnect", (reason) => {
      console.log("❌ WebSocket disconnected:", reason);
    });

    socket.on("connect_error", (error) => {
      console.error("❌ WebSocket connection error:", error.message);
    });

    // Notify the user when all reconnection attempts are exhausted
    socket.on("reconnect_failed", () => {
      console.error("🔌 WebSocket: all reconnection attempts failed. Please refresh the page.");
    });

    return socket;

  } catch (error) {
    console.error("[WebSocket] Init failed:", error.message);
    return null;
  }

};

/**
 * Disconnect from the WebSocket server
 */
export const disconnectSocket = () => {
  // Disconnect whether connected or reconnecting — both cases need cleanup
  // so the next login gets a fresh authenticated socket with the new token.
  if (socket) {
    console.log("⚠️ Disconnect requested");
    socket.disconnect();
    socket = null;
    console.log("🔌 WebSocket disconnected manually");
  }
};