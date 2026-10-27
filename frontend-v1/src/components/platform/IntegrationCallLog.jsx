import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getIntegrationCalls } from '../../core/services/admin.service';

const PROVIDERS = [
  ['', 'All services'], ['smtp', 'Email (SMTP)'], ['twilio', 'SMS / WhatsApp (Twilio)'], ['fcm', 'Mobile push (Firebase)'],
  ['anthropic', 'AI (Anthropic)'], ['razorpay', 'Payments (Razorpay)'], ['imap', 'Email replies (IMAP)'],
  ['hibp', 'Breached-password check'], ['ops_webhook', 'Ops alert webhook'],
];
const LABELS = Object.fromEntries(PROVIDERS);
const PAGE_SIZE = 50;
const selectClass = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

/**
 * INT-9: every call the CRM made to an outside service — when, what for, the outcome and how long it
 * took. Message text, recipients and keys are never logged; provider errors are shown with
 * addresses and numbers removed.
 */
const IntegrationCallLog = () => {
  const [filters, setFilters] = useState({ provider: '', outcome: '' });
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getIntegrationCalls({ provider: filters.provider || undefined, outcome: filters.outcome || undefined, page, size: PAGE_SIZE })
      .then((res) => !cancelled && setData(res))
      .catch((err) => !cancelled && setError(apiErrorMessage(err, 'Could not load the call log.')));
    return () => {
      cancelled = true;
    };
  }, [filters, page]);

  const set = (key) => (e) => {
    setPage(0);
    setFilters((prev) => ({ ...prev, [key]: e.target.value }));
  };
  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Integration call log</h2>
      <p className="text-sm text-slate-500 mt-1">Calls to outside services, kept for 90 days. Payloads are never stored.</p>

      {data?.lastDay?.length > 0 && (
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          {data.lastDay.map((p) => (
            <div key={p.provider} className="rounded-md border border-slate-100 bg-slate-50 px-3 py-2 text-sm">
              <p className="font-medium text-slate-800">{LABELS[p.provider] || p.provider}</p>
              <p className="text-slate-500">
                {p.calls} call{p.calls === 1 ? '' : 's'} in 24 h · {p.failures} failed · {p.averageMs} ms avg
              </p>
            </div>
          ))}
        </div>
      )}

      <div className="mt-3 flex flex-wrap gap-2">
        <select value={filters.provider} onChange={set('provider')} className={selectClass} aria-label="Service">
          {PROVIDERS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
        <select value={filters.outcome} onChange={set('outcome')} className={selectClass} aria-label="Outcome">
          <option value="">Any outcome</option>
          <option value="success">Succeeded</option>
          <option value="failed">Failed</option>
        </select>
      </div>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      <div className="mt-3 overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-slate-500 border-b border-slate-200">
              <th className="px-2 py-1.5 font-medium">When</th>
              <th className="px-2 py-1.5 font-medium">Service</th>
              <th className="px-2 py-1.5 font-medium">Call</th>
              <th className="px-2 py-1.5 font-medium">Outcome</th>
              <th className="px-2 py-1.5 font-medium">Time</th>
              <th className="px-2 py-1.5 font-medium">Tenant</th>
              <th className="px-2 py-1.5 font-medium">Detail</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {(data?.calls ?? []).map((c) => (
              <tr key={c.id}>
                <td className="px-2 py-1.5 whitespace-nowrap text-slate-600">{new Date(c.at).toLocaleString()}</td>
                <td className="px-2 py-1.5 whitespace-nowrap">{LABELS[c.provider] || c.provider}</td>
                <td className="px-2 py-1.5 whitespace-nowrap font-mono text-xs">{c.operation}</td>
                <td className="px-2 py-1.5 whitespace-nowrap">
                  <span className={c.outcome === 'failed' ? 'text-red-700' : 'text-green-700'}>
                    {c.outcome === 'failed' ? 'Failed' : 'OK'}{c.httpStatus ? ` (${c.httpStatus})` : ''}
                  </span>
                </td>
                <td className="px-2 py-1.5 whitespace-nowrap text-slate-600">{c.durationMs} ms</td>
                <td className="px-2 py-1.5 whitespace-nowrap text-slate-600">{c.tenantId ?? '—'}</td>
                <td className="px-2 py-1.5 text-slate-600">{c.error || ''}</td>
              </tr>
            ))}
            {data && data.calls.length === 0 && (
              <tr><td colSpan={7} className="px-2 py-4 text-center text-slate-400">No calls match.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {pages > 1 && (
        <div className="mt-3 flex items-center gap-2 text-sm">
          <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
          <span className="text-slate-500">Page {page + 1} of {pages}</span>
          <Button size="sm" variant="outline" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>Next</Button>
        </div>
      )}
    </div>
  );
};

export default IntegrationCallLog;
