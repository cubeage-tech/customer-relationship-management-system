/** React Query cache keys — one place, so invalidation after a write can't drift from the reads. */
export const QUERY_KEYS = {
  users: ['users'],
  dashboard: (module) => ['dashboard', module],
  quotations: (params) => ['quotations', params],
  tickets: (params) => ['tickets', params],
};
