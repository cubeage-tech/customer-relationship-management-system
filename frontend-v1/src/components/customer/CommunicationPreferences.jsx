import { useEffect, useState } from 'react';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getCustomerConsent, saveCustomerConsent } from '../../core/services/notification.service';

const SOURCES = {
  staff: 'recorded by your team',
  sms_stop: 'they replied STOP by SMS',
  sms_start: 'they replied START by SMS',
  whatsapp_stop: 'they replied STOP on WhatsApp',
  whatsapp_start: 'they replied START on WhatsApp',
};

const OPTIONS = [
  { key: 'marketingEmail', label: 'Marketing emails', hint: 'Campaign emails. They can also unsubscribe from any campaign email.' },
  { key: 'sms', label: 'SMS', hint: 'Updates and campaigns by text. Turned off when they reply STOP.' },
  { key: 'whatsapp', label: 'WhatsApp', hint: 'Only switch on when the customer agreed to WhatsApp messages.' },
];

/**
 * NOT-7: what this customer agreed to receive. Automated messages and campaigns follow it; a STOP
 * or START reply by SMS or WhatsApp changes it automatically. Changes are audited.
 */
const CommunicationPreferences = ({ customerId, canEdit }) => {
  const [result, setResult] = useState({ id: null, consent: null, error: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getCustomerConsent(customerId)
      .then((consent) => setResult({ id: customerId, consent, error: '' }))
      .catch((err) => setResult({ id: customerId, consent: null, error: apiErrorMessage(err, 'Could not load the preferences.') }));
  }, [customerId]);

  const consent = result.id === customerId ? result.consent : null;

  const toggle = async (key, value) => {
    setBusy(true);
    setError('');
    try {
      const saved = await saveCustomerConsent(customerId, { [key]: value });
      setResult({ id: customerId, consent: saved, error: '' });
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the preference.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
      <h2 className="font-semibold text-slate-900">Communication preferences</h2>
      {(result.error || error) && <p className="text-sm text-red-600 mt-2">{result.error || error}</p>}
      {consent && (
        <>
          <div className="grid gap-3 sm:grid-cols-3 mt-3 text-sm">
            {OPTIONS.map((o) => (
              <label key={o.key} className="flex items-start gap-2">
                <input type="checkbox" className="mt-0.5" checked={consent[o.key]} disabled={!canEdit || busy}
                  onChange={(e) => toggle(o.key, e.target.checked)} />
                <span>
                  <span className="text-slate-900">{o.label}</span>
                  <span className="block text-xs text-slate-500">{o.hint}</span>
                </span>
              </label>
            ))}
          </div>
          {consent.updatedAt && (
            <p className="text-xs text-slate-400 mt-3">
              Last changed {new Date(consent.updatedAt).toLocaleString()}{consent.source && SOURCES[consent.source] ? ` — ${SOURCES[consent.source]}` : ''}
            </p>
          )}
        </>
      )}
    </div>
  );
};

export default CommunicationPreferences;
