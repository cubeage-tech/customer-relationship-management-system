import { QueryClient } from '@tanstack/react-query';

/** HTTP statuses that will not succeed on retry — retrying only delays the error message. */
const NON_RETRYABLE = new Set([400, 401, 403, 404, 409, 422]);

export const QUERY_STALE_TIME_MS = 30_000;

/**
 * Shared cache for server data. Screens that use React Query get request de-duplication,
 * cancellation on unmount and a short stale time; screens that don't are unaffected.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: QUERY_STALE_TIME_MS,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) =>
        !NON_RETRYABLE.has(error?.response?.status) && failureCount < 2,
    },
    mutations: {
      retry: false,
    },
  },
});
