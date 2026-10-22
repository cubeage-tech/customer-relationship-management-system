import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getSmsAlerts, updateSmsAlerts } from '../../core/services/communication.service';

const EVENT_LABELS = {
  quotation_sent: 'Quotation sent to customer',
  ticket_status_changed: 'Service ticket status changes',
};

/** FR-5.3: tenant admin chooses which events send an SMS alert, and to whom. */
const SmsAlertSettings = () => {
  const [rules, setRules] = useState([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getSmsAlerts()
      .then((data) => setRules(data ?? []))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load SMS alert settings.')));
  }, []);

  const update = (event, patch) => {
    setMessage('');
    setRules((prev) => prev.map((r) => (r.event === event ? { ...r, ...patch } : r)));
  };

  const save = async () => {
    setSaving(true);
    setError('');
    try {
      setRules((await updateSmsAlerts(rules)) ?? rules);
      setMessage('SMS alert settings saved.');
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save SMS alert settings.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">SMS alerts</h2>
      <p className="text-sm text-slate-500 mt-1">
        Send an SMS automatically when these events happen. Every alert, sent or failed, is logged on the customer's communication timeline.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <div className="mt-3 divide-y divide-slate-100">
        {rules.map((rule) => (
          <div key={rule.event} className="flex flex-wrap items-center justify-between gap-3 py-3">
            <label className="flex items-center gap-2 text-sm text-slate-800">
              <input type="checkbox" checked={rule.enabled} onChange={(e) => update(rule.event, { enabled: e.target.checked })} />
              {EVENT_LABELS[rule.event] || rule.event}
            </label>
            <label className="text-sm text-slate-600 flex items-center gap-2">
              Send to
              <select
                value={rule.recipient}
                onChange={(e) => update(rule.event, { recipient: e.target.value })}
                className="border border-slate-300 rounded px-2 py-1"
              >
                <option value="customer">Customer</option>
                <option value="internal">{rule.event === 'ticket_status_changed' ? 'Assigned technician' : 'Quotation owner'}</option>
              </select>
            </label>
          </div>
        ))}
      </div>
      <div className="flex items-center gap-3 mt-3">
        <Button type="button" onClick={save} disabled={saving || rules.length === 0}>{saving ? 'Saving…' : 'Save'}</Button>
        {message && <span className="text-sm text-green-700">{message}</span>}
      </div>
    </div>
  );
};

export default SmsAlertSettings;
