import { useCallback, useEffect, useState } from 'react';
import Button from '../common/Button';
import MfaSetupCode from './MfaSetupCode';
import RecoveryCodes from './RecoveryCodes';
import { apiErrorMessage } from '../../core/utils/apiError';
import { beginMfaSetup, disableMfa, enableMfa, getMfaStatus, regenerateRecoveryCodes } from '../../core/services/mfa.service';

const codeInput = 'border border-slate-300 rounded px-2 py-1 text-sm font-mono tracking-widest w-32';

/**
 * SEC-1c: the user's own two-step sign-in — turn it on with an authenticator app, get new recovery
 * codes, or turn it off (not for roles that require it).
 */
const TwoStepSignIn = () => {
  const [status, setStatus] = useState(null);
  const [setup, setSetup] = useState(null); // { secret, otpauthUri } while setting up
  const [action, setAction] = useState(null); // 'disable' | 'codes'
  const [code, setCode] = useState('');
  const [codes, setCodes] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => getMfaStatus().then(setStatus).catch((err) => setError(apiErrorMessage(err, 'Could not load your sign-in settings.'))), []);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (fn) => {
    setBusy(true);
    setError('');
    try {
      await fn();
    } catch (err) {
      setError(apiErrorMessage(err, 'That didn\'t work.'));
    } finally {
      setBusy(false);
    }
  };

  const start = () => run(async () => {
    setCodes(null);
    setSetup(await beginMfaSetup());
    setCode('');
  });

  const confirm = (e) => {
    e.preventDefault();
    run(async () => {
      const res = await enableMfa(code.trim());
      setCodes(res.recoveryCodes);
      setSetup(null);
      setCode('');
      await load();
    });
  };

  const submitAction = (e) => {
    e.preventDefault();
    run(async () => {
      if (action === 'disable') {
        await disableMfa(code.trim());
      } else {
        setCodes((await regenerateRecoveryCodes(code.trim())).recoveryCodes);
      }
      setAction(null);
      setCode('');
      await load();
    });
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mt-6">
      <h2 className="font-semibold text-slate-900">Two-step sign-in</h2>
      <p className="text-sm text-slate-500 mt-1">A code from an authenticator app on your phone, as well as your password, each time you sign in.</p>
      {error && <p className="text-sm text-red-600 mt-2" role="alert">{error}</p>}

      {status && !setup && (
        <div className="mt-3 text-sm">
          {status.enabled ? (
            <p className="text-green-700">On since {new Date(status.enabledAt).toLocaleDateString()} · {status.recoveryCodesLeft} recovery code(s) left</p>
          ) : (
            <p className="text-slate-600">{status.required ? 'Required for your role — you\'ll set it up at your next sign-in.' : 'Off.'}</p>
          )}
          <div className="flex flex-wrap gap-2 mt-3">
            {!status.enabled && <Button size="sm" disabled={busy} onClick={start}>Turn on</Button>}
            {status.enabled && <Button size="sm" variant="outline" disabled={busy} onClick={() => { setAction('codes'); setCode(''); }}>New recovery codes</Button>}
            {status.enabled && !status.required && (
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => { setAction('disable'); setCode(''); }}>Turn off</Button>
            )}
          </div>
        </div>
      )}

      {setup && (
        <form onSubmit={confirm} className="mt-4 space-y-3">
          <MfaSetupCode secret={setup.secret} otpauthUri={setup.otpauthUri} />
          <label className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-slate-600">Code from the app</span>
            <input name="code" value={code} onChange={(e) => setCode(e.target.value)} required inputMode="numeric" autoComplete="one-time-code"
              maxLength={6} className={codeInput} />
            <Button type="submit" size="sm" disabled={busy}>Turn on</Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setSetup(null)}>Cancel</Button>
          </label>
        </form>
      )}

      {action && (
        <form onSubmit={submitAction} className="mt-4 flex flex-wrap items-center gap-2 text-sm">
          <span className="text-slate-600">{action === 'disable' ? 'Enter a code to turn it off' : 'Enter a code to replace your recovery codes'}</span>
          <input name="code" value={code} onChange={(e) => setCode(e.target.value)} required autoComplete="one-time-code" maxLength={20} className={codeInput} />
          <Button type="submit" size="sm" disabled={busy}>Confirm</Button>
          <Button type="button" size="sm" variant="ghost" onClick={() => setAction(null)}>Cancel</Button>
        </form>
      )}

      {codes && <div className="mt-4"><RecoveryCodes codes={codes} /></div>}
    </div>
  );
};

export default TwoStepSignIn;
