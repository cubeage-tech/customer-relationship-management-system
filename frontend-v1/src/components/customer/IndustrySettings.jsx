import { useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { useIndustries } from '../../core/hooks/useIndustries';
import { createIndustry, updateIndustry } from '../../core/services/industry.service';

/**
 * FR-1.6: tenant admin maintains the customer industry list. Entries are renamed or deactivated,
 * never deleted, so existing customers keep their industry.
 */
const IndustrySettings = () => {
  const { industries, reload } = useIndustries();
  const [newLabel, setNewLabel] = useState('');
  const [editing, setEditing] = useState(null); // { code, label }
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const run = async (action, fallback) => {
    setBusy(true);
    setError('');
    try {
      await action();
      await reload();
      return true;
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const add = async (e) => {
    e.preventDefault();
    if (await run(() => createIndustry({ label: newLabel }), 'Could not add this industry.')) setNewLabel('');
  };

  const rename = async (e) => {
    e.preventDefault();
    if (await run(() => updateIndustry(editing.code, { label: editing.label }), 'Could not rename this industry.')) setEditing(null);
  };

  const toggle = (industry) =>
    run(() => updateIndustry(industry.code, { active: !industry.active }), 'Could not update this industry.');

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Customer industries</h2>
      <p className="text-sm text-slate-500 mt-1">
        The list users pick from when classifying customers. Deactivated industries stay on existing customers but can't be chosen for new ones.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <div className="mt-3 divide-y divide-slate-100">
        {industries.map((industry) => (
          <div key={industry.code} className="flex flex-wrap items-center justify-between gap-3 py-2">
            {editing?.code === industry.code ? (
              <form onSubmit={rename} className="flex flex-wrap items-center gap-2">
                <input
                  value={editing.label}
                  onChange={(e) => setEditing({ ...editing, label: e.target.value })}
                  maxLength={60}
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                  aria-label="Industry name"
                  required
                />
                <Button type="submit" disabled={busy}>Save</Button>
                <button type="button" className="text-sm text-slate-500 hover:underline" onClick={() => setEditing(null)}>
                  Cancel
                </button>
              </form>
            ) : (
              <span className={`text-sm ${industry.active ? 'text-slate-800' : 'text-slate-400 line-through'}`}>{industry.label}</span>
            )}
            {editing?.code !== industry.code && (
              <div className="flex gap-3 text-sm">
                <button type="button" className="text-blue-600 hover:underline" onClick={() => setEditing({ code: industry.code, label: industry.label })}>
                  Rename
                </button>
                <button type="button" className="text-blue-600 hover:underline disabled:opacity-50" disabled={busy} onClick={() => toggle(industry)}>
                  {industry.active ? 'Deactivate' : 'Activate'}
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
      <form onSubmit={add} className="flex flex-wrap items-center gap-2 mt-3">
        <input
          value={newLabel}
          onChange={(e) => setNewLabel(e.target.value)}
          placeholder="New industry, e.g. Food Processing"
          maxLength={60}
          className="border border-slate-300 rounded px-2 py-1 text-sm"
          aria-label="New industry name"
          required
        />
        <Button type="submit" disabled={busy || !newLabel.trim()}>Add industry</Button>
      </form>
    </div>
  );
};

export default IndustrySettings;
