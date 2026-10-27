import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import Button from '../../components/common/Button';
import RoutePath from '../../core/constants/routes.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getNotificationDeliveries } from '../../core/services/notification.service';

const CHANNELS = { in_app: 'In-app', push: 'Push', email: 'Email', sms: 'SMS', whatsapp: 'WhatsApp' };
const STATUSES = ['queued', 'sent', 'delivered', 'read', 'failed', 'skipped'];
const STATUS_STYLE = {
  failed: 'bg-red-50 text-red-700',
  skipped: 'bg-slate-100 text-slate-600',
  queued: 'bg-amber-50 text-amber-700',
  delivered: 'bg-green-50 text-green-700',
  read: 'bg-green-50 text-green-700',
  sent: 'bg-primary/10 text-primary',
};
const RECORD = { customer: (id) => `/customers/${id}`, lead: (id) => `/leads/${id}` };
const input = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

/**
 * NOT-8: every notification the CRM sent — in-app, push, email, SMS and WhatsApp — with whether it
 * went out, was delivered or read, failed (and why), or was skipped (e.g. the customer opted out).
 */
const NotificationLog = () => {
  const [filters, setFilters] = useState({ channel: '', status: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ key: null, rows: [], error: '' });
  const key = JSON.stringify([filters, page]);

  useEffect(() => {
    const k = JSON.stringify([filters, page]);
    getNotificationDeliveries({ ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v)), page })
      .then((rows) => setResult({ key: k, rows: rows ?? [], error: '' }))
      .catch((err) => setResult({ key: k, rows: [], error: apiErrorMessage(err, 'Could not load the delivery log.') }));
  }, [filters, page]);

  const set = (patch) => {
    setPage(0);
    setFilters((f) => ({ ...f, ...patch }));
  };
  const loaded = result.key === key;

  return (
    <section>
      <PageHeader title="Notification log" subtitle="What was sent, through which channel, and whether it arrived." />
      <p className="text-sm mb-4"><Link to={RoutePath.ADMIN_SETTINGS} className="text-primary hover:underline">Notification settings</Link></p>

      <div className="flex flex-wrap gap-3 bg-white border border-slate-200 rounded-lg p-4 mb-4 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Channel</span>
          <select value={filters.channel} onChange={(e) => set({ channel: e.target.value })} className={input}>
            <option value="">All</option>
            {Object.entries(CHANNELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Status</span>
          <select value={filters.status} onChange={(e) => set({ status: e.target.value })} className={input}>
            <option value="">All</option>
            {STATUSES.map((s) => <option key={s} value={s}>{s[0].toUpperCase() + s.slice(1)}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">From</span>
          <input type="date" value={filters.from} onChange={(e) => set({ from: e.target.value })} className={input} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">To</span>
          <input type="date" value={filters.to} onChange={(e) => set({ to: e.target.value })} className={input} />
        </label>
      </div>

      {!loaded && <p className="text-sm text-slate-400">Loading…</p>}
      {loaded && result.error && <p className="text-sm text-red-600">{result.error}</p>}
      {loaded && !result.error && result.rows.length === 0 && <p className="text-sm text-slate-400">Nothing sent yet for these filters.</p>}
      {loaded && result.rows.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                {['When', 'Event', 'Channel', 'To', 'Status', 'Details'].map((h) => <th key={h} className="px-3 py-2 font-medium whitespace-nowrap">{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((r) => (
                <tr key={r.id} className="border-b border-slate-100 align-top">
                  <td className="px-3 py-2 whitespace-nowrap text-slate-600">{new Date(r.createdAt).toLocaleString()}</td>
                  <td className="px-3 py-2 text-slate-800">{r.eventLabel}</td>
                  <td className="px-3 py-2 text-slate-600">{CHANNELS[r.channel] || r.channel}</td>
                  <td className="px-3 py-2">
                    {RECORD[r.recipientType] && r.recipientId
                      ? <Link to={RECORD[r.recipientType](r.recipientId)} className="text-primary hover:underline">{r.recipientName || r.recipientType}</Link>
                      : <span className="text-slate-700">{r.recipientName || r.recipientType}</span>}
                  </td>
                  <td className="px-3 py-2">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium capitalize ${STATUS_STYLE[r.status] || ''}`}>{r.status}</span>
                  </td>
                  <td className="px-3 py-2 text-slate-500 max-w-sm">{r.detail || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="flex gap-2 mt-3">
        <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Newer</Button>
        <Button size="sm" variant="outline" disabled={!loaded || result.rows.length < 100} onClick={() => setPage((p) => p + 1)}>Older</Button>
      </div>
    </section>
  );
};

export default NotificationLog;
