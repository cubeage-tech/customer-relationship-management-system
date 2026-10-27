import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getAuditEvents } from '../../core/services/audit.service';

const ENTITY_TYPES = ['customer', 'quotation', 'opportunity', 'commission', 'commission_rule', 'payment', 'subscription',
  'user', 'tenant', 'sales_team', 'service_ticket'];
const PAGE_SIZE = 50;
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

const humanize = (v) => (v ? v.toLowerCase().replaceAll('_', ' ').replace(/^\w/, (c) => c.toUpperCase()) : '');
const compact = (filters) => Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '' && v != null));

/**
 * NFR-7.2d: the audit trail — who did what to which record, and when. showTenant adds the tenant
 * column and filter (platform admin, all tenants).
 */
const AuditLogTable = ({ showTenant = false }) => {
  const [filters, setFilters] = useState({ from: '', to: '', entityType: '', action: '', tenantId: '' });
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ key: null, data: null, error: '' });
  const requestKey = JSON.stringify([filters, page]);

  useEffect(() => {
    const key = JSON.stringify([filters, page]);
    getAuditEvents({ ...compact(filters), page, size: PAGE_SIZE })
      .then((data) => setResult({ key, data, error: '' }))
      .catch((err) => setResult({ key, data: null, error: apiErrorMessage(err, 'Could not load the audit log.') }));
  }, [filters, page]);

  const set = (key) => (e) => {
    setPage(0);
    setFilters({ ...filters, [key]: e.target.value });
  };

  const loaded = result.key === requestKey;
  const data = result.data;
  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  return (
    <div>
      <div className="flex flex-wrap items-end gap-3 bg-white border border-slate-200 rounded-lg p-4 mb-4 text-sm">
        <label className="flex flex-col gap-1"><span className="text-slate-500">From</span>
          <input type="date" value={filters.from} onChange={set('from')} className={inputClass} /></label>
        <label className="flex flex-col gap-1"><span className="text-slate-500">To</span>
          <input type="date" value={filters.to} onChange={set('to')} className={inputClass} /></label>
        <label className="flex flex-col gap-1"><span className="text-slate-500">Record</span>
          <select value={filters.entityType} onChange={set('entityType')} className={inputClass}>
            <option value="">All records</option>
            {ENTITY_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select></label>
        <label className="flex flex-col gap-1"><span className="text-slate-500">Action</span>
          <select value={filters.action} onChange={set('action')} className={inputClass}>
            <option value="">All actions</option>
            {(data?.actions ?? []).map((a) => <option key={a} value={a}>{humanize(a)}</option>)}
          </select></label>
        {showTenant && (
          <label className="flex flex-col gap-1"><span className="text-slate-500">Tenant ID</span>
            <input type="number" min={1} value={filters.tenantId} onChange={set('tenantId')} className={`${inputClass} w-28`} placeholder="All" /></label>
        )}
      </div>

      {!loaded && <p className="text-sm text-slate-400">Loading…</p>}
      {loaded && result.error && <p className="text-sm text-red-600">{result.error}</p>}
      {loaded && data && (
        <div className="bg-white border border-slate-200 rounded-lg">
          {data.items.length === 0 ? (
            <p className="text-sm text-slate-400 p-5">No audit events match these filters.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-slate-500">
                    <th className="px-3 py-2 font-medium whitespace-nowrap">When</th>
                    {showTenant && <th className="px-3 py-2 font-medium">Tenant</th>}
                    <th className="px-3 py-2 font-medium">Who</th>
                    <th className="px-3 py-2 font-medium">Action</th>
                    <th className="px-3 py-2 font-medium">Record</th>
                    <th className="px-3 py-2 font-medium">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((e) => (
                    <tr key={e.id} className="border-b border-slate-100 align-top">
                      <td className="px-3 py-2 whitespace-nowrap text-slate-600">{new Date(e.createdAt).toLocaleString()}</td>
                      {showTenant && <td className="px-3 py-2 whitespace-nowrap">{e.tenantName ?? '—'}</td>}
                      <td className="px-3 py-2 whitespace-nowrap">{e.actorName}</td>
                      <td className="px-3 py-2 whitespace-nowrap font-medium text-slate-800">{humanize(e.action)}</td>
                      <td className="px-3 py-2 whitespace-nowrap">{humanize(e.entityType)}{e.entityId ? ` #${e.entityId}` : ''}</td>
                      <td className="px-3 py-2 text-slate-600 min-w-[16rem]">{e.details}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="flex items-center justify-between gap-3 p-3 border-t border-slate-100 text-sm text-slate-500">
            <span>{data.total} event(s) · page {data.page + 1} of {pages}</span>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
              <Button size="sm" variant="outline" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AuditLogTable;
