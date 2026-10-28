import { useEffect, useState } from 'react';
import { CalendarDays } from 'lucide-react';
import Button from '../common/Button';
import MeetingRecordings from './MeetingRecordings';
import MeetingConversation from './MeetingConversation';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import {
  cancelMeeting,
  downloadMeetingIcs,
  getCalendarFeed,
  listMeetings,
  resetCalendarFeed,
  scheduleMeeting,
  updateMeeting,
} from '../../core/services/meeting.service';

const REMINDERS = [
  { value: '', label: 'No reminder' },
  { value: '15', label: '15 minutes before' },
  { value: '60', label: '1 hour before' },
  { value: '1440', label: '1 day before' },
];

const DURATIONS = [15, 30, 45, 60, 90, 120];

const toLocalInput = (date) => {
  const d = new Date(date);
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

const nextHour = () => {
  const d = new Date();
  d.setHours(d.getHours() + 1, 0, 0, 0);
  return toLocalInput(d);
};

const emptyForm = () => ({ title: '', startsAt: nextHour(), duration: '30', location: '', notes: '', reminderMinutes: '15', includeCustomer: false });

const formatWhen = (m) => {
  const start = new Date(m.startsAt);
  const end = new Date(m.endsAt);
  return `${start.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })} – ${end.toLocaleTimeString([], { timeStyle: 'short' })}`;
};

const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

/**
 * FR-5.5: meetings with this customer (or lead). (a) schedule, reschedule, cancel; (b) an optional
 * reminder to the organizer and, when included, the customer; (c) each meeting downloads as .ics and
 * every user can subscribe to their own calendar feed.
 */
const MeetingsPanel = ({ customerId, leadId }) => {
  const { can } = usePermissions();
  const canSchedule = can(PERMISSIONS.COMMUNICATIONS_SEND);
  const scope = customerId ? { customerId } : { leadId };

  const [meetings, setMeetings] = useState([]);
  const [error, setError] = useState('');
  const [form, setForm] = useState(null); // null = closed; { id? ...fields }
  const [recordingsOf, setRecordingsOf] = useState(null); // FR-AI-7.1: meeting id whose recordings are open
  const [conversationVersion, setConversationVersion] = useState(0); // FR-AI-7.2: bumped when a recording saves a transcript
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedUrl, setFeedUrl] = useState('');

  const refresh = () =>
    listMeetings(scope)
      .then((data) => setMeetings(data ?? []))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load meetings.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId, leadId]);

  const change = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
  };

  const edit = (m) => setForm({
    id: m.id,
    title: m.title,
    startsAt: m.startsAt.slice(0, 16),
    duration: String(Math.round((new Date(m.endsAt) - new Date(m.startsAt)) / 60000)),
    location: m.location || '',
    notes: m.notes || '',
    reminderMinutes: m.reminderMinutes ? String(m.reminderMinutes) : '',
    includeCustomer: m.includeCustomer,
  });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setFormError('');
    const start = new Date(form.startsAt);
    const payload = {
      ...scope,
      title: form.title,
      startsAt: form.startsAt,
      endsAt: toLocalInput(new Date(start.getTime() + Number(form.duration) * 60000)),
      location: form.location,
      notes: form.notes,
      reminderMinutes: form.reminderMinutes ? Number(form.reminderMinutes) : null,
      includeCustomer: form.includeCustomer,
    };
    try {
      if (form.id) {
        await updateMeeting(form.id, payload);
      } else {
        await scheduleMeeting(payload);
      }
      setForm(null);
      refresh();
    } catch (err) {
      setFormError(apiErrorMessage(err, 'Could not save this meeting.'));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async (m) => {
    if (!window.confirm(`Cancel "${m.title}"? Invitees are sent a cancellation.`)) return;
    try {
      await cancelMeeting(m.id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not cancel this meeting.'));
    }
  };

  const showFeed = (reset) =>
    (reset ? resetCalendarFeed() : getCalendarFeed())
      .then((data) => setFeedUrl(data?.url || ''))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load your calendar link.')));

  const upcoming = meetings.filter((m) => m.status === 'scheduled' && new Date(m.endsAt) > new Date());
  const past = meetings.filter((m) => !upcoming.includes(m));

  const row = (m) => (
    <li key={m.id} className="py-3 flex flex-wrap items-start justify-between gap-3">
      <div className="text-sm">
        <p className={`font-medium ${m.status === 'cancelled' ? 'text-slate-400 line-through' : 'text-slate-900'}`}>{m.title}</p>
        <p className="text-slate-600">{formatWhen(m)}{m.location ? ` · ${m.location}` : ''}</p>
        <p className="text-xs text-slate-500">
          {m.organizerName}
          {m.reminderMinutes ? ` · reminder ${REMINDERS.find((r) => r.value === String(m.reminderMinutes))?.label || `${m.reminderMinutes} min before`}` : ''}
          {m.includeCustomer ? ' · customer invited' : ''}
          {m.status === 'cancelled' ? ' · cancelled' : ''}
        </p>
      </div>
      <div className="flex gap-3 text-sm">
        <button type="button" className="text-blue-600 hover:underline" onClick={() => downloadMeetingIcs(m.id, m.title)}>Add to calendar</button>
        <button type="button" className="text-blue-600 hover:underline" aria-expanded={recordingsOf === m.id}
          onClick={() => setRecordingsOf((id) => (id === m.id ? null : m.id))}>Recording &amp; summary</button>
        {canSchedule && m.status === 'scheduled' && (
          <>
            <button type="button" className="text-blue-600 hover:underline" onClick={() => edit(m)}>Reschedule</button>
            <button type="button" className="text-red-600 hover:underline" onClick={() => cancel(m)}>Cancel</button>
          </>
        )}
      </div>
      {/* FR-AI-7.1 */}
      {recordingsOf === m.id && (
        <>
          <MeetingRecordings meeting={m} canRecord={canSchedule} onTranscript={() => setConversationVersion((v) => v + 1)} />
          {/* FR-AI-7.2 */}
          <MeetingConversation meeting={m} canEdit={canSchedule} version={conversationVersion} />
        </>
      )}
    </li>
  );

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><CalendarDays size={18} aria-hidden="true" />Meetings</h2>
        {canSchedule && !form && <Button onClick={() => setForm(emptyForm())}>Schedule meeting</Button>}
      </div>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      {form && (
        <form onSubmit={submit} className="mt-4 grid gap-3 sm:grid-cols-2 rounded-md bg-slate-50 p-4 text-sm">
          <label className="sm:col-span-2">Title
            <input name="title" value={form.title} onChange={change} maxLength={200} required className={inputClass} placeholder="e.g. Product demo" />
          </label>
          <label>Starts
            <input type="datetime-local" name="startsAt" value={form.startsAt} onChange={change} required className={inputClass} />
          </label>
          <label>Duration
            <select name="duration" value={form.duration} onChange={change} className={inputClass}>
              {DURATIONS.map((d) => <option key={d} value={d}>{d < 60 ? `${d} min` : `${d / 60} h`}</option>)}
            </select>
          </label>
          <label>Location / link
            <input name="location" value={form.location} onChange={change} maxLength={300} className={inputClass} placeholder="Office, address or video link" />
          </label>
          <label>Reminder
            <select name="reminderMinutes" value={form.reminderMinutes} onChange={change} className={inputClass}>
              {REMINDERS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
          </label>
          <label className="sm:col-span-2">Notes
            <textarea name="notes" value={form.notes} onChange={change} rows={2} className={inputClass} />
          </label>
          <label className="sm:col-span-2 flex items-center gap-2">
            <input type="checkbox" name="includeCustomer" checked={form.includeCustomer} onChange={change} />
            Email the invite{form.reminderMinutes ? ' and reminder' : ''} to the {customerId ? 'customer' : 'lead'} too
          </label>
          {formError && <p className="text-red-600 sm:col-span-2">{formError}</p>}
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" disabled={busy}>{busy ? 'Saving…' : form.id ? 'Save changes' : 'Schedule'}</Button>
            <Button type="button" variant="outline" onClick={() => setForm(null)}>Close</Button>
          </div>
        </form>
      )}

      {meetings.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">No meetings yet.</p>
      ) : (
        <>
          {upcoming.length > 0 && <ul className="mt-2 divide-y divide-slate-100">{upcoming.map(row)}</ul>}
          {past.length > 0 && (
            <details className="mt-2">
              <summary className="cursor-pointer text-sm text-slate-500">Past and cancelled ({past.length})</summary>
              <ul className="divide-y divide-slate-100">{past.map(row)}</ul>
            </details>
          )}
        </>
      )}

      <div className="mt-4 border-t border-slate-100 pt-3 text-sm text-slate-600">
        {feedUrl ? (
          <div className="space-y-1">
            <p>Subscribe to this private link in Google Calendar, Outlook or Apple Calendar to see your meetings there:</p>
            <div className="flex flex-wrap items-center gap-2">
              <input readOnly value={feedUrl} className="border border-slate-300 rounded px-2 py-1 flex-1 min-w-0 text-xs" aria-label="Calendar feed link" onFocus={(e) => e.target.select()} />
              <button type="button" className="text-blue-600 hover:underline" onClick={() => navigator.clipboard?.writeText(feedUrl).catch(() => {})}>Copy</button>
              <button type="button" className="text-slate-500 hover:underline" onClick={() => showFeed(true)}>Replace link</button>
            </div>
          </div>
        ) : (
          <button type="button" className="text-blue-600 hover:underline" onClick={() => showFeed(false)}>Show my calendar subscription link</button>
        )}
      </div>
    </div>
  );
};

export default MeetingsPanel;
