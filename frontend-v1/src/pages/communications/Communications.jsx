import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CalendarDays, Mail, MessageCircle, MessageSquare, Phone } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import RecordPicker from '../../components/communication/RecordPicker';
import CommunicationTimeline from '../../components/communication/CommunicationTimeline';
import MeetingsPanel from '../../components/meeting/MeetingsPanel';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getInbox } from '../../core/services/communication.service';
import { listMeetings } from '../../core/services/meeting.service';

const CHANNELS = {
  email: { label: 'Email', Icon: Mail },
  whatsapp: { label: 'WhatsApp', Icon: MessageCircle },
  sms: { label: 'SMS', Icon: MessageSquare },
  call: { label: 'Call', Icon: Phone },
  meeting: { label: 'Meeting', Icon: CalendarDays },
};
const TABS = [
  { key: 'inbox', label: 'Unified inbox' },
  { key: 'calls', label: 'Call log' },
  { key: 'meetings', label: 'Meeting scheduler' },
];
const selectClass = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';
const card = 'bg-white border border-slate-200 rounded-lg';

const recordLink = (type, id) => (type === 'customer' ? `/customers/${id}` : `/leads/${id}`);
const words = (v) => (v ? v.replace(/_/g, ' ') : '');
const duration = (s) => (s == null ? '' : s >= 60 ? `${Math.floor(s / 60)} min ${s % 60 ? `${s % 60} s` : ''}`.trim() : `${s} s`);

/** The latest messages and calls across the customers and leads the user can see. */
const useInbox = (params) => {
  const [result, setResult] = useState({ key: null, items: [], error: '' });
  const key = JSON.stringify(params);
  useEffect(() => {
    const k = JSON.stringify(params);
    // `reload` only forces a new request; it isn't sent.
    getInbox({ ...Object.fromEntries(Object.entries(params).filter(([k, v]) => v && k !== 'reload')), limit: 100 })
      .then((items) => setResult({ key: k, items: items ?? [], error: '' }))
      .catch((err) => setResult({ key: k, items: [], error: apiErrorMessage(err, 'Could not load messages.') }));
    // key captures params
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return { ...result, loaded: result.key === key };
};

/** UI-1: emails, WhatsApp, SMS and calls in one list; each opens its record's timeline. */
const Inbox = () => {
  const [filters, setFilters] = useState({ channel: '', direction: '' });
  const { items, error, loaded } = useInbox(filters);
  return (
    <>
      <div className={`flex flex-wrap gap-3 ${card} p-4 mb-4 text-sm`}>
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
      {loaded && error && <p className="text-sm text-red-600">{error}</p>}
      {loaded && !error && items.length === 0 && (
        <EmptyState title="No messages yet" message="Emails, WhatsApp messages, SMS and calls with your customers and leads will appear here." />
      )}
      {loaded && items.length > 0 && (
        <ul className={`${card} divide-y divide-slate-100`}>
          {items.map(({ entry: c, recordType, recordId, recordName }) => {
            const { label, Icon } = CHANNELS[c.channel] || CHANNELS.call;
            return (
              <li key={c.id}>
                <Link to={recordLink(recordType, recordId)} className="flex gap-3 p-4 hover:bg-slate-50">
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
    </>
  );
};

/** Choose a customer or lead, then work on it inline (log a call, schedule a meeting). */
const WithRecord = ({ title, children }) => {
  const [record, setRecord] = useState(null);
  return (
    <div className={`${card} p-5 mb-6`}>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <h2 className="font-semibold text-slate-900">{title}</h2>
        {record && <Button size="sm" variant="ghost" onClick={() => setRecord(null)}>Choose someone else</Button>}
      </div>
      {!record ? <RecordPicker onPick={setRecord} /> : (
        <>
          <p className="text-sm text-slate-500 mb-3">
            With <Link to={recordLink(record.type, record.id)} className="text-primary hover:underline">{record.name}</Link>
          </p>
          {children(record)}
        </>
      )}
    </div>
  );
};

/** NAV-M06: every call logged across the user's customers and leads, and logging a new one. */
const CallLog = ({ canLog }) => {
  const [direction, setDirection] = useState('');
  const [reload, setReload] = useState(0);
  const { items, error, loaded } = useInbox({ channel: 'call', direction, reload });
  return (
    <>
      {canLog && (
        <WithRecord title="Log a call">
          {(r) => (
            <CommunicationTimeline {...(r.type === 'customer' ? { customerId: r.id } : { leadId: r.id })} />
          )}
        </WithRecord>
      )}
      <div className="flex flex-wrap items-end gap-3 mb-3 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Direction</span>
          <select value={direction} onChange={(e) => setDirection(e.target.value)} className={selectClass}>
            <option value="">All calls</option>
            <option value="inbound">Incoming</option>
            <option value="outbound">Outgoing</option>
          </select>
        </label>
        <Button size="sm" variant="ghost" onClick={() => setReload((n) => n + 1)}>Refresh</Button>
      </div>
      {!loaded && <p className="text-sm text-slate-400">Loading…</p>}
      {loaded && error && <p className="text-sm text-red-600">{error}</p>}
      {loaded && !error && items.length === 0 && <EmptyState title="No calls logged" message="Calls logged against customers and leads appear here." />}
      {loaded && items.length > 0 && (
        <div className={`${card} overflow-x-auto`}>
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                {['When', 'With', 'Direction', 'Outcome', 'Duration', 'Notes', 'Logged by'].map((h) => <th key={h} className="px-3 py-2 font-medium whitespace-nowrap">{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {items.map(({ entry: c, recordType, recordId, recordName }) => (
                <tr key={c.id} className="border-b border-slate-100 align-top">
                  <td className="px-3 py-2 whitespace-nowrap text-slate-600">{new Date(c.occurredAt).toLocaleString()}</td>
                  <td className="px-3 py-2"><Link to={recordLink(recordType, recordId)} className="text-primary hover:underline">{recordName}</Link></td>
                  <td className="px-3 py-2 text-slate-600">{c.direction === 'inbound' ? 'Incoming' : 'Outgoing'}</td>
                  <td className="px-3 py-2 capitalize text-slate-600">{words(c.callOutcome) || '—'}</td>
                  <td className="px-3 py-2 whitespace-nowrap text-slate-600">{duration(c.callDurationSeconds) || '—'}</td>
                  <td className="px-3 py-2 text-slate-500 max-w-xs truncate" title={c.body || ''}>{c.body || '—'}</td>
                  <td className="px-3 py-2 text-slate-500">{c.createdByName || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
};

/** NAV-M06: my upcoming meetings, and scheduling one with any customer or lead. */
const MeetingScheduler = ({ canSchedule }) => {
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState({ key: null, meetings: [], error: '' });
  useEffect(() => {
    listMeetings({})
      .then((m) => setResult({ key: reload, meetings: m ?? [], error: '' }))
      .catch((err) => setResult({ key: reload, meetings: [], error: apiErrorMessage(err, 'Could not load meetings.') }));
  }, [reload]);
  const loaded = result.key === reload;

  return (
    <>
      {canSchedule && (
        <WithRecord title="Schedule a meeting">
          {(r) => (
            <MeetingsPanel {...(r.type === 'customer' ? { customerId: r.id } : { leadId: r.id })} />
          )}
        </WithRecord>
      )}
      <div className="flex items-center justify-between gap-2 mb-3">
        <h2 className="font-semibold text-slate-900">My upcoming meetings</h2>
        <Button size="sm" variant="ghost" onClick={() => setReload((n) => n + 1)}>Refresh</Button>
      </div>
      {!loaded && <p className="text-sm text-slate-400">Loading…</p>}
      {loaded && result.error && <p className="text-sm text-red-600">{result.error}</p>}
      {loaded && !result.error && result.meetings.length === 0 && <EmptyState title="No upcoming meetings" message="Meetings you organise appear here." />}
      {loaded && result.meetings.length > 0 && (
        <ul className={`${card} divide-y divide-slate-100`}>
          {result.meetings.map((m) => {
            const type = m.customerId ? 'customer' : 'lead';
            const id = m.customerId ?? m.leadId;
            return (
              <li key={m.id} className="flex flex-wrap items-baseline justify-between gap-2 p-4 text-sm">
                <div className="min-w-0">
                  <p className="font-medium text-slate-900">{m.title}</p>
                  <p className="text-slate-500">
                    {id ? <Link to={recordLink(type, id)} className="text-primary hover:underline">{m.customerName || m.leadName}</Link> : null}
                    {m.location ? ` · ${m.location}` : ''}
                    {m.status === 'cancelled' ? ' · Cancelled' : ''}
                  </p>
                </div>
                <span className="text-slate-600 whitespace-nowrap">
                  {new Date(m.startsAt).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
};

/**
 * UI-1 / NAV-M06 (SRS §11.1, 12.4): Communications → Unified inbox, Call log and Meeting
 * scheduler. The tab is in the address (?tab=calls) so each screen can be linked to.
 */
const Communications = () => {
  const { can } = usePermissions();
  const [params, setParams] = useSearchParams();
  const tab = TABS.some((t) => t.key === params.get('tab')) ? params.get('tab') : 'inbox';
  const canSend = can(PERMISSIONS.COMMUNICATIONS_SEND);

  return (
    <section>
      <PageHeader title="Communications" subtitle="Emails, WhatsApp, SMS, calls and meetings across your customers and leads." />
      <div className="flex flex-wrap gap-2 mb-4" role="tablist">
        {TABS.map((t) => (
          <button key={t.key} type="button" role="tab" aria-selected={tab === t.key}
            onClick={() => setParams(t.key === 'inbox' ? {} : { tab: t.key }, { replace: true })}
            className={`px-3 py-1.5 rounded-md text-sm border ${tab === t.key
              ? 'border-primary/50 bg-primary/5 text-primary'
              : 'border-slate-200 bg-white text-slate-600 hover:border-primary/50'}`}>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'inbox' && <Inbox />}
      {tab === 'calls' && <CallLog canLog={canSend} />}
      {tab === 'meetings' && <MeetingScheduler canSchedule={canSend} />}
    </section>
  );
};

export default Communications;
