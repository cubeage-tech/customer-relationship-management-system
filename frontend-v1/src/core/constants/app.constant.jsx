export const APPLICATION_CONSTANTS = {
  STORAGE: {
    TOKEN: "token",
    USER_DETAILS: "user",
    LANGUAGE: "lang",
    ROUTE_STEP_1: "routeStep1",
    ROUTE_STEP_2: "routeStep2",
  },
  // FIX: Only image types are accepted by the backend (JPEG, PNG, WebP, HEIC).
  // PDF and XLS were listed here but the backend rejects them — removed to
  // prevent confusing UX where users select a file that then gets rejected.
  ALLOW_FILES_EXTENSION: "image/jpeg,image/png,image/webp,image/heic",
  CONTENT_TYPES: "application/json",
};

export const USER_ROLES = {
  ADMIN: "ADMIN"
};