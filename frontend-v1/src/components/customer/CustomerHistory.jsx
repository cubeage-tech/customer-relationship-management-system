import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getCustomerHistory } from '../../core/services/admin.service';

const TYPES = [
  { key: '', label: 'Everything' },
  { key: 'deal', label: 'Deals' },
  { key: 'quotation', label: 'Quotations' },
  { key: 'ticket', label: 'Service' },
  { key: 'message', label: 'Messages & calls' },
  { key: 'meeting', label: 'Meetings' },
  { key: 'document', label: 'Documents' },
  { key: 'change', label: 'Changes' },
];
const LINKS = {
  opportunity: (id) => `/opportunities/${id}`,
  quotation: (id) => `/quotations/${id}`,
  service_ticket: (id) => `/service-tickets/${id}`,
};
const DOT = {
  deal: 'bg-primary', quotation: 'bg-amber-500', ticket: 'bg-red-500', message: 'bg-slate-400',
  meeting: 'bg-emerald-500', document: 'bg-sky-500', change: 'bg-slate-300', customer: 'bg-primary',
};

/** API-G02 (FR-1.3/1.4): everything that happened with the customer, newest first, filterable by kind. */
const CustomerHistory = ({ customerId }) => {
  const [type, setType] = useState('');
  const [result, setResult] = useState({ key: null, events: [], error: '' });
  const key = `${customerId}:${type}`;

  useEffect(() => {
    const k = `${customerId}:${type}`;
    getCustomerHistory(customerId, type || undefined)
      .then((events) => setResult({ key: k, events: events ?? [], error: '' }))
      .catch((err) => setResult({ key: k, events: [], error: apiErrorMessage(err, 'Could not load the history.') }));
  }, [customerId, type]);

  const loaded = result.key === key;

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold text-slate-900">History</h2>
        <div className="flex flex-wrap gap-1" role="tablist" aria-label="Kind of event">
          {TYPES.map((t) => (
            <button key={t.key} type="button" role="tab" aria-selected={type === t.key} onClick={() => setType(t.key)}
              className={`px-2.5 py-1 rounded-full text-xs border ${type === t.key ? 'border-primary bg-primary/5 text-primary' : 'border-slate-200 text-slate-600 hover:border-primary/50'}`}>
              {t.label}
            </button>
          ))}
        </div>
      </div>
      {!loaded && <p className="text-sm text-slate-400 mt-3">Loading…</p>}
      {loaded && result.error && <p className="text-sm text-red-600 mt-3">{result.error}</p>}
      {loaded && !result.error && result.events.length === 0 && <p className="text-sm text-slate-400 mt-3">Nothing yet.</p>}
      {loaded && result.events.length > 0 && (
        <ol className="mt-4 space-y-3 border-l border-slate-200 pl-4 max-h-[28rem] overflow-y-auto">
          {result.events.map((e, i) => {
            const link = LINKS[e.recordType]?.(e.recordId);
            return (
              <li key={`${e.type}-${e.recordId}-${e.at}-${i}`} className="relative text-sm">
                <span className={`absolute -left-[1.3rem] top-1.5 size-2 rounded-full ${DOT[e.type] || 'bg-slate-300'}`} aria-hidden="true" />
                <p className="text-slate-900">
                  {link ? <Link to={link} className="hover:underline">{e.title}</Link> : e.title}
                  <span className="text-xs text-slate-400"> · {e.at ? new Date(e.at).toLocaleString() : ''}</span>
                </p>
                {e.detail && <p className="text-slate-500 break-words">{e.detail}</p>}
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
};

export default CustomerHistory;
