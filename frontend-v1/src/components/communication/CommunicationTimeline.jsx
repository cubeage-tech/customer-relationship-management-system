import { useEffect, useState } from 'react';
import { CalendarDays, Mail, MessageCircle, MessageSquare, Phone } from 'lucide-react';
import Button from '../common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listCommunications, logCall, sendEmail, sendWhatsApp } from '../../core/services/communication.service';

const CHANNEL = {
  email: { label: 'Email', Icon: Mail },
  whatsapp: { label: 'WhatsApp', Icon: MessageCircle },
  sms: { label: 'SMS', Icon: MessageSquare },
  call: { label: 'Call', Icon: Phone },
  // FR-5.6: meetings (FR-5.5) share the timeline, placed at their start time.
  meeting: { label: 'Meeting', Icon: CalendarDays },
};

const OUTCOMES = [
  { value: 'connected', label: 'Connected' },
  { value: 'no_answer', label: 'No answer' },
  { value: 'busy', label: 'Busy' },
  { value: 'voicemail', label: 'Voicemail' },
  { value: 'wrong_number', label: 'Wrong number' },
];

const STATUS_CLASS = {
  failed: 'bg-red-50 text-red-700',
  delivered: 'bg-green-50 text-green-700',
  read: 'bg-green-50 text-green-700',
  received: 'bg-indigo-50 text-indigo-700',
};

const label = (value) => (value ? value.charAt(0).toUpperCase() + value.slice(1).replace('_', ' ') : '');

const nowLocal = () => {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

/**
 * FR-5.6: one chronological timeline of emails, WhatsApp messages, SMS, calls and meetings for a customer
 * (or a not-yet-converted lead), with actions to log a call (FR-5.4), send an email (FR-5.1) or a
 * WhatsApp message (FR-5.2).
 */
const CommunicationTimeline = ({ customerId, leadId, defaultEmail = '', defaultPhone = '' }) => {
  const { can } = usePermissions();
  const canSend = can(PERMISSIONS.COMMUNICATIONS_SEND);
  const scope = customerId ? { customerId } : { leadId };

  const [items, setItems] = useState([]);
  const [loadError, setLoadError] = useState('');
  const [mode, setMode] = useState(null); // 'call' | 'email' | 'whatsapp'
  const [form, setForm] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const refresh = () =>
    listCommunications(scope)
      .then((data) => {
        setItems(data ?? []);
        setLoadError('');
      })
      .catch((err) => setLoadError(apiErrorMessage(err, 'Could not load the communication timeline.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId, leadId]);

  const open = (next) => {
    setFormError('');
    setMode(mode === next ? null : next);
    if (next === 'call') setForm({ direction: 'outbound', outcome: 'connected', minutes: '', notes: '', occurredAt: nowLocal(), phone: defaultPhone });
    if (next === 'email') setForm({ to: defaultEmail, subject: '', body: '' });
    if (next === 'whatsapp') setForm({ to: defaultPhone, body: '' });
  };

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setFormError('');
    try {
      let saved;
      if (mode === 'call') {
        saved = await logCall({
          ...scope,
          direction: form.direction,
          outcome: form.outcome,
          durationSeconds: form.minutes === '' ? null : Math.round(Number(form.minutes) * 60),
          notes: form.notes,
          phone: form.phone,
          occurredAt: form.occurredAt ? `${form.occurredAt}:00` : null,
        });
      } else if (mode === 'email') {
        saved = await sendEmail({ ...scope, to: form.to, subject: form.subject, body: form.body });
      } else {
        saved = await sendWhatsApp({ ...scope, to: form.to, body: form.body });
      }
      if (saved?.status === 'failed') {
        setFormError(saved.errorMessage || 'The message could not be sent.');
      } else {
        setMode(null);
      }
      refresh();
    } catch (err) {
      setFormError(apiErrorMessage(err, 'Could not save this communication.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 className="font-semibold text-slate-900">Communication timeline</h2>
        {canSend && (
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="secondary" onClick={() => open('call')}>Log call</Button>
            <Button type="button" variant="secondary" onClick={() => open('email')}>Send email</Button>
            <Button type="button" variant="secondary" onClick={() => open('whatsapp')}>Send WhatsApp</Button>
          </div>
        )}
      </div>

      {mode && (
        <form onSubmit={submit} className="mb-5 grid gap-3 sm:grid-cols-2 border border-slate-200 rounded-lg p-4 bg-slate-50">
          {mode === 'call' && (
            <>
              <label className="text-sm">Direction
                <select name="direction" value={form.direction} onChange={change} className={inputClass}>
                  <option value="outbound">Outbound (we called)</option>
                  <option value="inbound">Inbound (they called)</option>
                </select>
              </label>
              <label className="text-sm">Outcome
                <select name="outcome" value={form.outcome} onChange={change} className={inputClass}>
                  {OUTCOMES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </label>
              <label className="text-sm">When
                <input type="datetime-local" name="occurredAt" value={form.occurredAt} max={nowLocal()} onChange={change} className={inputClass} />
              </label>
              <label className="text-sm">Duration (minutes)
                <input type="number" min="0" step="0.5" name="minutes" value={form.minutes} onChange={change} className={inputClass} />
              </label>
              <label className="text-sm">Phone
                <input name="phone" value={form.phone} onChange={change} className={inputClass} />
              </label>
              <label className="text-sm sm:col-span-2">Notes
                <textarea name="notes" rows={3} value={form.notes} onChange={change} className={inputClass} />
              </label>
            </>
          )}
          {mode === 'email' && (
            <>
              <label className="text-sm">To
                <input type="email" name="to" required value={form.to} onChange={change} className={inputClass} />
              </label>
              <label className="text-sm">Subject
                <input name="subject" required maxLength={255} value={form.subject} onChange={change} className={inputClass} />
              </label>
              <label className="text-sm sm:col-span-2">Message
                <textarea name="body" required rows={5} value={form.body} onChange={change} className={inputClass} />
              </label>
            </>
          )}
          {mode === 'whatsapp' && (
            <>
              <label className="text-sm">To (phone with country code)
                <input name="to" required value={form.to} onChange={change} className={inputClass} placeholder="+919876543210" />
              </label>
              <label className="text-sm sm:col-span-2">Message
                <textarea name="body" required maxLength={1600} rows={4} value={form.body} onChange={change} className={inputClass} />
              </label>
            </>
          )}
          {formError && <p className="text-sm text-red-600 sm:col-span-2">{formError}</p>}
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Saving…' : mode === 'call' ? 'Log call' : 'Send'}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setMode(null)}>Cancel</Button>
          </div>
        </form>
      )}

      {loadError ? (
        <p className="text-sm text-red-600">{loadError}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-slate-500">No emails, WhatsApp messages, SMS or calls recorded yet.</p>
      ) : (
        <ol className="space-y-3">
          {items.map((c) => {
            const { label: channelLabel, Icon } = CHANNEL[c.channel] || CHANNEL.call;
            const inbound = c.direction === 'inbound';
            return (
              <li key={`${c.channel}-${c.id}`} className="flex gap-3 border-b border-slate-100 pb-3 last:border-0">
                <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full bg-slate-100 text-slate-600">
                  <Icon size={16} aria-hidden="true" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-medium text-slate-900">
                      {c.channel === 'meeting'
                        ? channelLabel
                        : `${inbound ? `${channelLabel} from` : `${channelLabel} to`} ${c.counterpart || (c.channel === 'call' ? 'customer' : '—')}`}
                    </span>
                    {c.callOutcome && <span className="text-slate-600">· {label(c.callOutcome)}</span>}
                    {c.callDurationSeconds != null && <span className="text-slate-600">· {Math.round(c.callDurationSeconds / 60)} min</span>}
                    {c.channel !== 'call' && (
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASS[c.status] || 'bg-slate-100 text-slate-600'}`}>
                        {label(c.status)}
                      </span>
                    )}
                  </div>
                  {c.subject && <p className="text-sm font-medium text-slate-800 mt-1">{c.subject}</p>}
                  {c.body && <p className="text-sm text-slate-600 mt-1 whitespace-pre-wrap break-words">{c.body}</p>}
                  {c.errorMessage && <p className="text-xs text-red-600 mt-1">{c.errorMessage}</p>}
                  <p className="text-xs text-slate-400 mt-1">
                    {new Date(c.occurredAt).toLocaleString()}
                    {c.createdByName ? ` · ${c.createdByName}` : inbound ? '' : ' · automatic'}
                  </p>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
};

export default CommunicationTimeline;
