import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Button from '../common/Button';
import RoutePath from '../../core/constants/routes.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getNotificationSettings, saveNotificationSettings } from '../../core/services/notification.service';

const CHANNELS = [
  { key: 'in_app', label: 'In-app & push' },
  { key: 'email', label: 'Email' },
  { key: 'sms', label: 'SMS' },
  { key: 'whatsapp', label: 'WhatsApp' },
];
const AUDIENCE = { customer: 'To the customer', staff: 'To your team' };

/**
 * NOT-6: the tenant admin chooses which events notify, and through which channels. SMS also picks
 * who receives it (customer events); WhatsApp only reaches customers who opted in (NOT-7).
 */
const NotificationSettings = () => {
  const [events, setEvents] = useState([]);
  const [draft, setDraft] = useState({});
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => getNotificationSettings()
    .then((data) => { setEvents(data ?? []); setDraft({}); })
    .catch((err) => setError(apiErrorMessage(err, 'Could not load the notification settings.'))), []);

  useEffect(() => {
    load();
  }, [load]);

  const key = (event, channel) => `${event}:${channel}`;
  const cell = (e, c) => ({ ...c, ...draft[key(e.event, c.channel)] });
  const change = (e, c, patch) => {
    setSaved(false);
    setDraft((d) => ({ ...d, [key(e.event, c.channel)]: { ...cell(e, c), ...patch } }));
  };

  const save = async () => {
    setBusy(true);
    setError('');
    try {
      const changes = Object.entries(draft).map(([k, v]) => {
        const [event, channel] = k.split(':');
        return { event, channel, enabled: v.enabled, recipient: v.recipient ?? null };
      });
      setEvents(await saveNotificationSettings(changes));
      setDraft({});
      setSaved(true);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the notification settings.'));
    } finally {
      setBusy(false);
    }
  };

  const groups = ['customer', 'staff'].map((a) => ({ audience: a, rows: events.filter((e) => e.audience === a) }));

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="font-semibold text-slate-900">Notifications</h2>
          <p className="text-sm text-slate-500 mt-1">Which events notify, and how. Customer messages respect each customer&apos;s preferences.</p>
        </div>
        <Link to={RoutePath.ADMIN_NOTIFICATION_LOG} className="text-sm text-primary hover:underline">Delivery log</Link>
      </div>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      {saved && <p className="text-sm text-green-700 mt-3">Saved.</p>}

      <div className="overflow-x-auto mt-3">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-slate-500 border-b border-slate-200">
              <th className="py-2 pr-4 font-medium">Event</th>
              {CHANNELS.map((c) => <th key={c.key} className="py-2 px-3 font-medium whitespace-nowrap">{c.label}</th>)}
            </tr>
          </thead>
          {groups.map((g) => (
            <tbody key={g.audience}>
              <tr><td colSpan={5} className="pt-4 pb-1 text-xs font-semibold uppercase text-slate-400">{AUDIENCE[g.audience]}</td></tr>
              {g.rows.map((e) => (
                <tr key={e.event} className="border-b border-slate-100 align-top">
                  <td className="py-2 pr-4 text-slate-800">{e.label}</td>
                  {CHANNELS.map((col) => {
                    const c = e.channels.find((x) => x.channel === col.key);
                    if (!c) return <td key={col.key} className="py-2 px-3 text-slate-300">—</td>;
                    const v = cell(e, c);
                    return (
                      <td key={col.key} className="py-2 px-3">
                        <label className="inline-flex items-center gap-1.5">
                          <input type="checkbox" checked={v.enabled} disabled={!c.available && !v.enabled}
                            onChange={(ev) => change(e, c, { enabled: ev.target.checked })}
                            aria-label={`${e.label} by ${col.label}`} />
                        </label>
                        {col.key === 'sms' && e.audience === 'customer' && (
                          <select value={v.recipient ?? 'customer'} onChange={(ev) => change(e, c, { recipient: ev.target.value })}
                            className="block mt-1 border border-slate-300 rounded px-1 py-0.5 text-xs bg-white" aria-label={`${e.label} SMS recipient`}>
                            <option value="customer">to customer</option>
                            <option value="internal">to owner</option>
                          </select>
                        )}
                        {c.note && <span className="block mt-1 text-[11px] text-slate-400 max-w-[12rem]">{c.note}</span>}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          ))}
        </table>
      </div>
      <div className="mt-3">
        <Button size="sm" disabled={busy || Object.keys(draft).length === 0} onClick={save}>{busy ? 'Saving…' : 'Save'}</Button>
      </div>
    </div>
  );
};

export default NotificationSettings;
