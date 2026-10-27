import { useCallback, useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { createIntegrationKey, getIntegrationStatus, listIntegrationKeys, revokeIntegrationKey } from '../../core/services/admin.service';

/**
 * NAV-M11: Settings → Integrations — which services the CRM is set up with (and any credential
 * problem, INT-8), and the API keys an ERP uses to sync (API-G12). A new key is shown once.
 */
const IntegrationSettings = () => {
  const [status, setStatus] = useState([]);
  const [keys, setKeys] = useState([]);
  const [name, setName] = useState('');
  const [created, setCreated] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => Promise.all([getIntegrationStatus(), listIntegrationKeys()])
    .then(([s, k]) => { setStatus(s ?? []); setKeys(k ?? []); })
    .catch((err) => setError(apiErrorMessage(err, 'Could not load the integrations.'))), []);

  useEffect(() => {
    load();
  }, [load]);

  const create = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      setCreated(await createIntegrationKey(name.trim()));
      setName('');
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not create the key.'));
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (k) => {
    if (!window.confirm(`Revoke "${k.name}"? The ERP using it stops syncing at once.`)) return;
    try {
      await revokeIntegrationKey(k.id);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not revoke the key.'));
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Integrations</h2>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <ul className="mt-3 grid gap-2 sm:grid-cols-2 text-sm">
        {status.map((s) => (
          <li key={s.key} className="rounded-md border border-slate-100 p-3">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium text-slate-900">{s.label}</span>
              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${s.problem ? 'bg-red-50 text-red-700'
                : s.configured ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-600'}`}>
                {s.problem ? 'Needs attention' : s.configured ? 'Connected' : 'Not set up'}
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-1">{s.problem || s.detail}</p>
          </li>
        ))}
      </ul>

      <h3 className="text-sm font-semibold text-slate-700 mt-5">ERP API keys</h3>
      <p className="text-xs text-slate-500">Your ERP sends one in the X-Api-Key header to sync customers, deals, quotations and products.</p>
      {created && (
        <div className="mt-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm">
          <p className="font-medium text-amber-900">Copy this key into your ERP now — it won&apos;t be shown again.</p>
          <p className="mt-1 font-mono break-all select-all text-slate-900">{created.apiKey}</p>
          <button type="button" className="mt-1 text-xs underline text-amber-900" onClick={() => setCreated(null)}>Done</button>
        </div>
      )}
      <ul className="mt-2 divide-y divide-slate-100 text-sm">
        {keys.map((k) => (
          <li key={k.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
            <span className={k.revokedAt ? 'text-slate-400 line-through' : 'text-slate-800'}>
              {k.name} <span className="font-mono text-xs text-slate-400">{k.keyPrefix}…</span>
              <span className="text-xs text-slate-400"> · {k.lastUsedAt ? `last used ${new Date(k.lastUsedAt).toLocaleString()}` : 'never used'}</span>
            </span>
            {!k.revokedAt && <Button size="sm" variant="ghost" onClick={() => revoke(k)}>Revoke</Button>}
          </li>
        ))}
      </ul>
      <form onSubmit={create} className="flex flex-wrap items-center gap-2 mt-3 text-sm">
        <input name="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} placeholder="Key name, e.g. Tally ERP"
          className="border border-slate-300 rounded px-2 py-1" />
        <Button type="submit" size="sm" disabled={busy}>Create key</Button>
      </form>
    </div>
  );
};

export default IntegrationSettings;
