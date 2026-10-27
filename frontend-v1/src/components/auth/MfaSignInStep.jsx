import { useEffect, useState } from 'react';
import MfaSetupCode from './MfaSetupCode';
import RecoveryCodes from './RecoveryCodes';
import { apiErrorMessage } from '../../core/utils/apiError';
import { confirmMfaSetup, startMfaSetup, verifyMfa } from '../../core/services/mfa.service';

const input = 'w-full px-4 py-3 border border-gray-200 rounded-xl text-center text-lg tracking-[0.3em] font-mono focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/50';

/**
 * SEC-1c: the second sign-in step, after the password. Either enter the code from the authenticator
 * app (or a recovery code), or — the first time, for a role that requires it — set the app up,
 * confirm it with a code and save the recovery codes. Calls onSignedIn({ user, token }) at the end.
 */
const MfaSignInStep = ({ mfaToken, setup, onSignedIn, onCancel }) => {
  const [secret, setSecret] = useState(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [finished, setFinished] = useState(null); // { user, token, recoveryCodes } after setup

  useEffect(() => {
    if (!setup) return;
    startMfaSetup(mfaToken).then(setSecret).catch((err) => setError(apiErrorMessage(err, 'Could not start the setup. Sign in again.')));
  }, [setup, mfaToken]);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (setup) {
        setFinished(await confirmMfaSetup(mfaToken, code.trim()));
      } else {
        onSignedIn(await verifyMfa(mfaToken, code.trim()));
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'That code isn\'t right.'));
      if (err.response?.data?.errorCode === 'MFA_CHALLENGE_EXPIRED') onCancel(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (finished) {
    return (
      <div className="space-y-5">
        <h2 className="text-xl font-bold text-gray-900">Two-step sign-in is on</h2>
        <RecoveryCodes codes={finished.recoveryCodes ?? []} />
        <button type="button" onClick={() => onSignedIn(finished)}
          className="w-full py-3 rounded-xl bg-primary text-white font-semibold hover:bg-primary/90">I&apos;ve saved them — continue</button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-5">
      <div>
        <h2 className="text-xl font-bold text-gray-900">{setup ? 'Set up two-step sign-in' : 'Two-step sign-in'}</h2>
        <p className="text-sm text-gray-500 mt-1">
          {setup
            ? 'Your role requires a code from an authenticator app each time you sign in.'
            : 'Enter the 6-digit code from your authenticator app. Lost your phone? Enter one of your recovery codes.'}
        </p>
      </div>
      {setup && secret && <MfaSetupCode secret={secret.secret} otpauthUri={secret.otpauthUri} />}
      <div>
        <label htmlFor="mfa-code" className="block text-sm font-semibold text-gray-700 mb-1.5">{setup ? 'Code from the app' : 'Code'}</label>
        <input id="mfa-code" name="code" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus
          autoComplete="one-time-code" inputMode={setup ? 'numeric' : 'text'} maxLength={20} className={input} />
      </div>
      {error && <p className="text-sm text-red-600" role="alert">{error}</p>}
      <button type="submit" disabled={busy || (setup && !secret)}
        className="w-full py-3 rounded-xl bg-primary text-white font-semibold hover:bg-primary/90 disabled:opacity-60">
        {busy ? 'Checking…' : setup ? 'Turn on and sign in' : 'Sign in'}
      </button>
      <button type="button" onClick={() => onCancel('')} className="w-full text-sm text-gray-500 hover:underline">Back to sign in</button>
    </form>
  );
};

export default MfaSignInStep;
