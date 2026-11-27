import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getTenantSettings, updateTenantSettings } from '../../core/services/audit.service';

const FIELDS = [
  { key: 'slaHoursCritical', label: 'Critical', suffix: 'hours' },
  { key: 'slaHoursHigh', label: 'High', suffix: 'hours' },
  { key: 'slaHoursMedium', label: 'Medium', suffix: 'hours' },
  { key: 'slaHoursLow', label: 'Low', suffix: 'hours' },
];
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm w-24';

/** Form values: a number when customised, '' when using the deployment default. */
const toForm = (s) => Object.fromEntries(
  [...FIELDS.map((f) => f.key), 'discountApprovalThreshold'].map((k) => [k, s[k].custom ? String(s[k].value) : '']),
);

/**
 * NFR-7.6b: this tenant's SLA resolution windows per ticket priority and the discount % that needs
 * approval. Leave a value empty to use the default shown. Changes apply to new tickets and to
 * quotations when they are next saved.
 */
const ThresholdSettings = () => {
  const [settings, setSettings] = useState(null);
  const [form, setForm] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getTenantSettings()
      .then((s) => {
        setSettings(s);
        setForm(toForm(s));
      })
      .catch((err) => setError(apiErrorMessage(err, 'Could not load the thresholds.')));
  }, []);

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v === '' ? null : Number(v)]));
      const saved = await updateTenantSettings(body);
      setSettings(saved);
      setForm(toForm(saved));
      setNotice('Thresholds saved.');
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the thresholds.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">SLA &amp; approval thresholds</h2>
      <p className="text-sm text-slate-500 mt-1">
        How long each ticket priority has to be resolved, and the line-item discount that needs approval.
        Leave a field empty to use the default.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      {notice && <p className="text-sm text-green-700 mt-3">{notice}</p>}
      {form && settings && (
        <form onSubmit={save} className="mt-4 space-y-4">
          <fieldset>
            <legend className="text-sm font-medium text-slate-700 mb-2">Ticket SLA (time to resolve)</legend>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {FIELDS.map((f) => (
                <label key={f.key} className="flex flex-col gap-1 text-sm">
                  <span className="text-slate-500">{f.label}</span>
                  <span className="flex items-center gap-1">
                    <input
                      type="number" min={1} max={2160} className={inputClass}
                      value={form[f.key]} placeholder={String(settings[f.key].defaultValue)}
                      onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                    />
                    <span className="text-slate-400">{f.suffix}</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-slate-700 font-medium">Discount needing approval</span>
            <span className="flex items-center gap-1">
              <span className="text-slate-500">above</span>
              <input
                type="number" min={0} max={100} step="0.5" className={inputClass}
                value={form.discountApprovalThreshold} placeholder={String(settings.discountApprovalThreshold.defaultValue)}
                onChange={(e) => setForm({ ...form, discountApprovalThreshold: e.target.value })}
              />
              <span className="text-slate-400">%</span>
            </span>
          </label>
          <Button type="submit" size="sm" disabled={busy}>{busy ? 'Saving…' : 'Save thresholds'}</Button>
        </form>
      )}
    </div>
  );
};

export default ThresholdSettings;
