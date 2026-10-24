import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, Mail, MessageCircle, MessageSquare, Phone } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getInbox } from '../../core/services/communication.service';

const CHANNELS = {
  email: { label: 'Email', Icon: Mail },
  whatsapp: { label: 'WhatsApp', Icon: MessageCircle },
  sms: { label: 'SMS', Icon: MessageSquare },
  call: { label: 'Call', Icon: Phone },
  meeting: { label: 'Meeting', Icon: CalendarDays },
};
const selectClass = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

/**
 * UI-1 (SRS §11.1 Communications): the unified inbox — the latest emails, WhatsApp messages, SMS
 * and calls across the customers and leads you can see. Each opens its record's timeline, where
 * you reply, call or schedule a meeting.
 */
const Communications = () => {
  const [filters, setFilters] = useState({ channel: '', direction: '' });
  const [result, setResult] = useState({ key: null, items: [], error: '' });
  const key = JSON.stringify(filters);

  useEffect(() => {
    const k = JSON.stringify(filters);
    getInbox({ ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v)), limit: 100 })
      .then((items) => setResult({ key: k, items: items ?? [], error: '' }))
      .catch((err) => setResult({ key: k, items: [], error: apiErrorMessage(err, 'Could not load the inbox.') }));
  }, [filters]);

  const loaded = result.key === key;

  return (
    <section>
      <PageHeader title="Communications" subtitle="Emails, WhatsApp, SMS and calls across your customers and leads." />

      <div className="flex flex-wrap gap-3 bg-white border border-slate-200 rounded-lg p-4 mb-4 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Channel</span>
          <select value={filters.channel} onChange={(e) => setFilters({ ...filters, channel: e.target.value })} className={selectClass}>
            <option value="">All channels</option>
            {['email', 'whatsapp', 'sms', 'call'].map((c) => <option key={c} value={c}>{CHANNELS[c].label}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Direction</span>
          <select value={filters.direction} onChange={(e) => setFilters({ ...filters, direction: e.target.value })} className={selectClass}>
            <option value="">In and out</option>
            <option value="inbound">Received</option>
            <option value="outbound">Sent</option>
          </select>
        </label>
      </div>

      {!loaded && <p className="text-sm text-slate-400">Loading…</p>}
      {loaded && result.error && <p className="text-sm text-red-600">{result.error}</p>}
      {loaded && !result.error && result.items.length === 0 && (
        <EmptyState title="No messages yet" message="Emails, WhatsApp messages, SMS and calls with your customers and leads will appear here." />
      )}
      {loaded && result.items.length > 0 && (
        <ul className="bg-white border border-slate-200 rounded-lg divide-y divide-slate-100">
          {result.items.map(({ entry: c, recordType, recordId, recordName }) => {
            const { label, Icon } = CHANNELS[c.channel] || CHANNELS.call;
            const to = recordType === 'customer' ? `/customers/${recordId}` : `/leads/${recordId}`;
            return (
              <li key={c.id}>
                <Link to={to} className="flex gap-3 p-4 hover:bg-slate-50">
                  <Icon size={18} className="text-slate-400 mt-0.5 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <p className="text-sm font-medium text-slate-900 truncate">
                        {recordName}
                        <span className="text-slate-400 font-normal"> · {label} {c.direction === 'inbound' ? 'received' : 'sent'}</span>
                      </p>
                      <span className="text-xs text-slate-400 whitespace-nowrap">{new Date(c.occurredAt).toLocaleString()}</span>
                    </div>
                    {c.subject && <p className="text-sm text-slate-700 truncate">{c.subject}</p>}
                    {c.body && <p className="text-sm text-slate-500 truncate">{c.body}</p>}
                    {c.status === 'failed' && <p className="text-xs text-red-600">Not delivered{c.errorMessage ? ` — ${c.errorMessage}` : ''}</p>}
                  </div>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
};

export default Communications;
