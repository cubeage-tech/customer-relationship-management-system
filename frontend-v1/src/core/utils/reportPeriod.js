const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const PERIODS = [
  { value: 'month', label: 'This month' },
  { value: 'quarter', label: 'This quarter' },
  { value: 'year', label: 'This year' },
  { value: 'last30', label: 'Last 30 days' },
];

/** { from, to } for a named period, ending today. */
export const periodRange = (period) => {
  const today = new Date();
  let start;
  if (period === 'quarter') start = new Date(today.getFullYear(), Math.floor(today.getMonth() / 3) * 3, 1);
  else if (period === 'year') start = new Date(today.getFullYear(), 0, 1);
  else if (period === 'last30') start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 29);
  else start = new Date(today.getFullYear(), today.getMonth(), 1);
  return { from: iso(start), to: iso(today) };
};

/** Drops empty filters so they aren't sent. */
export const reportParams = (filters) =>
  Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '' && v != null));

export const formatMoney = (v) =>
  v == null ? '—' : Number(v).toLocaleString(undefined, { maximumFractionDigits: 0 });

export const formatPercent = (v) => (v == null ? '—' : `${v > 0 ? '+' : ''}${v}%`);
