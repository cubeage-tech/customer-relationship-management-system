/** Fallback messages per HTTP status, for when the backend didn't send a usable one. */
const STATUS_MESSAGES = {
  400: 'Some of the details are invalid. Check them and try again.',
  401: 'Your session has ended. Please sign in again.',
  402: 'Your subscription has expired. Ask your administrator to renew it.',
  403: "You don't have permission to do this.",
  404: 'This item no longer exists or is not available to you.',
  409: 'Someone else changed this at the same time. Refresh and try again.',
  500: 'Something went wrong on our side. Please try again in a moment.',
};

export const apiStatus = (error) => error?.response?.status;

export const isForbidden = (error) => apiStatus(error) === 403;

/**
 * A message that is safe to show the user: the backend's own message when it sent one,
 * otherwise a sensible default for the status, otherwise `fallback`.
 */
export const apiErrorMessage = (error, fallback = 'Something went wrong. Please try again.') => {
  if (!error?.response) {
    return error?.message === 'Network Error'
      ? 'Cannot reach the server. Check your connection and try again.'
      : error?.message || fallback;
  }
  const status = error.response.status;
  const serverMessage = error.response.data?.message;
  // 5xx bodies are generic or internal — always show our own wording for them.
  if (serverMessage && status < 500) return serverMessage;
  return STATUS_MESSAGES[status] || STATUS_MESSAGES[Math.floor(status / 100) * 100] || fallback;
};
