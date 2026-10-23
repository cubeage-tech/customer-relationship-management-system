import { useEffect, useState } from 'react';
import { getSalesPerformance } from '../../core/services/opportunity.service';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

const winRate = (row) => {
  const closed = row.wonCount + row.lostCount;
  return closed === 0 ? '—' : `${Math.round((row.wonCount / closed) * 100)}%`;
};

const VIEWS = [
  ['byOwner', 'By salesperson'],
  ['byTeam', 'By team'],
];

/**
 * FR-3.5: individual and team performance — open, won and lost opportunity value — over the
 * opportunities the viewer may see (the server applies the data scope).
 */
const SalesPerformance = () => {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [view, setView] = useState('byOwner');

  useEffect(() => {
    getSalesPerformance()
      .then((result) => setData(result ?? { byOwner: [], byTeam: [] }))
      .catch(() => setError('Could not load sales performance.'));
  }, []);

  const rows = data?.[view] ?? [];

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 sm:p-6" aria-labelledby="sales-performance-heading">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="sales-performance-heading" className="text-base font-bold text-slate-900">Sales performance</h2>
          <p className="mt-1 text-xs text-slate-500">Open, won and lost opportunity value. Win rate counts closed deals only.</p>
        </div>
        <div className="inline-flex rounded-md border border-slate-200 p-0.5" role="group" aria-label="Group performance">
          {VIEWS.map(([value, label]) => (
            <button
              key={value}
              type="button"
              onClick={() => setView(value)}
              aria-pressed={view === value}
              className={`h-8 rounded px-3 text-xs font-semibold ${view === value ? 'bg-violet-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <p className="text-sm text-red-600">{error}</p>
      ) : !data ? (
        <div className="h-24 animate-pulse rounded-md bg-slate-100" role="status" aria-label="Loading sales performance" />
      ) : rows.length === 0 ? (
        <p className="py-4 text-center text-sm text-slate-500">No opportunities yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2.5 font-semibold">{view === 'byOwner' ? 'Salesperson' : 'Team'}</th>
                <th className="px-4 py-2.5 text-right font-semibold">Open</th>
                <th className="px-4 py-2.5 text-right font-semibold">Won</th>
                <th className="px-4 py-2.5 text-right font-semibold">Lost</th>
                <th className="px-4 py-2.5 text-right font-semibold">Win rate</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((row) => (
                <tr key={row.id ?? 'none'}>
                  <td className="px-4 py-3 font-medium text-slate-800">{row.name}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(row.openValue)} <span className="text-xs text-slate-400">({row.openCount})</span></td>
                  <td className="px-4 py-3 text-right font-semibold text-emerald-700">{formatCurrency(row.wonValue)} <span className="text-xs font-normal text-slate-400">({row.wonCount})</span></td>
                  <td className="px-4 py-3 text-right text-rose-700">{formatCurrency(row.lostValue)} <span className="text-xs text-slate-400">({row.lostCount})</span></td>
                  <td className="px-4 py-3 text-right text-slate-700">{winRate(row)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default SalesPerformance;
