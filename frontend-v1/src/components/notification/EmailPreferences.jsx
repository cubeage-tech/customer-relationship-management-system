import { useEffect, useState } from 'react';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getNotificationPreferences, saveNotificationPreferences } from '../../core/services/rolePermission.service';

/** NOT-1: the user turns their weekly digest email on or off. */
const EmailPreferences = () => {
  const [prefs, setPrefs] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getNotificationPreferences().then(setPrefs).catch((err) => setError(apiErrorMessage(err, 'Could not load your email preferences.')));
  }, []);

  const toggle = async (e) => {
    const weeklyDigest = e.target.checked;
    setError('');
    try {
      setPrefs(await saveNotificationPreferences({ weeklyDigest }));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save your email preferences.'));
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mt-6">
      <h2 className="font-semibold text-slate-900">Email notifications</h2>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
      {prefs && (
        <label className="flex items-start gap-2 mt-3 text-sm">
          <input type="checkbox" className="mt-0.5" checked={prefs.weeklyDigest} onChange={toggle} />
          <span>
            <span className="text-slate-900">Weekly digest</span>
            <span className="block text-slate-500">Every Monday: last week&apos;s leads, conversions and deals won, and what&apos;s due for you this week.</span>
          </span>
        </label>
      )}
      <p className="text-xs text-slate-400 mt-3">Follow-up reminders and meeting invites are always sent.</p>
    </div>
  );
};

export default EmailPreferences;
