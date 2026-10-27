import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import Button from '../common/Button';
import RoutePath from '../../core/constants/routes.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getIntegrationHealth, resolveIntegration } from '../../core/services/notification.service';

const STATUS = {
  ok: { label: 'Working', className: 'bg-green-50 text-green-700' },
  auth_failed: { label: 'Credentials rejected', className: 'bg-red-50 text-red-700' },
  unknown: { label: 'Not used yet', className: 'bg-slate-100 text-slate-600' },
};

const useHealth = () => {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const load = useCallback(() => getIntegrationHealth()
    .then((data) => setRows(data ?? []))
    .catch((err) => setError(apiErrorMessage(err, 'Could not load the integrations.'))), []);
  useEffect(() => {
    load();
  }, [load]);
  return { rows, error, load, setError };
};

/**
 * INT-8: each outside service the CRM signs in to — whether it still accepts the CRM's credentials,
 * and how to re-authorise one that stopped. Marked fixed after updating the credential.
 */
const IntegrationHealth = () => {
  const { rows, error, load, setError } = useHealth();
  const [busy, setBusy] = useState('');

  const resolve = async (key) => {
    setBusy(key);
    try {
      await resolveIntegration(key);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not update this integration.'));
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Integrations</h2>
      <p className="text-sm text-slate-500 mt-1">
        When a service rejects the CRM&apos;s credentials (a revoked or expired key, a changed password), you are notified here, by
        in-app notification and by email.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <ul className="mt-3 divide-y divide-slate-100">
        {(rows ?? []).map((r) => {
          const s = STATUS[r.status] || STATUS.unknown;
          return (
            <li key={r.integration} className="py-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium text-slate-900">{r.label}</span>
                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${s.className}`}>{s.label}</span>
              </div>
              {r.status === 'auth_failed' && (
                <div className="mt-2 rounded-md bg-red-50 p-3 text-red-800">
                  <p>Rejected since {new Date(r.failedAt).toLocaleString()}{r.lastError ? ` — ${r.lastError}` : ''}.</p>
                  <p className="mt-1">{r.fix}</p>
                  <Button size="sm" variant="outline" className="mt-2" disabled={busy === r.integration} onClick={() => resolve(r.integration)}>
                    I&apos;ve re-authorised it
                  </Button>
                </div>
              )}
              {r.status === 'ok' && r.lastOkAt && <p className="text-xs text-slate-400 mt-1">Last worked {new Date(r.lastOkAt).toLocaleString()}</p>}
            </li>
          );
        })}
      </ul>
    </div>
  );
};

/** INT-8: a dashboard banner while any integration needs re-authorising. */
export const IntegrationAlertBanner = () => {
  const { rows } = useHealth();
  const failing = (rows ?? []).filter((r) => r.status === 'auth_failed');
  if (failing.length === 0) return null;
  return (
    <div className="mb-4 flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert">
      <KeyRound size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
      <p>
        {failing.map((r) => r.label).join(', ')} {failing.length === 1 ? 'needs' : 'need'} re-authorising.{' '}
        <Link to={RoutePath.PLATFORM_SETTINGS} className="font-semibold underline">See how</Link>
      </p>
    </div>
  );
};

export default IntegrationHealth;
