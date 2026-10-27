import { useEffect, useState } from 'react';
import AiPerformance from './AiPerformance';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getAiSettings, saveAiSettings } from '../../core/services/ai.service';

/**
 * SEC-5/SEC-6: what reaches the AI provider. Switch AI off, or keep message text (emails, WhatsApp,
 * SMS) away from it; each feature lists what it sends. Single customers or leads are excluded from
 * their own page.
 */
const AiDataSettings = () => {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiSettings().then(setData).catch((err) => setError(apiErrorMessage(err, 'Could not load the AI settings.')));
  }, []);

  const change = async (patch) => {
    setError('');
    try {
      const settings = await saveAiSettings({ ...data.settings, ...patch });
      setData((d) => ({ ...d, settings }));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the AI settings.'));
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">AI &amp; data</h2>
      <p className="text-sm text-slate-500 mt-1">
        Email addresses, phone numbers and ID numbers are always removed before anything is sent to the AI provider.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      {data && (
        <>
          <div className="mt-3 space-y-2 text-sm">
            <label className="flex items-start gap-2">
              <input type="checkbox" className="mt-0.5" checked={data.settings.aiEnabled} onChange={(e) => change({ aiEnabled: e.target.checked })} />
              <span><span className="text-slate-900">AI features on</span>
                <span className="block text-slate-500">Off: no data goes to the AI provider; lead scores and forecasts still work (they are calculated in the CRM).</span></span>
            </label>
            <label className="flex items-start gap-2">
              <input type="checkbox" className="mt-0.5" checked={data.settings.messageContent} disabled={!data.settings.aiEnabled}
                onChange={(e) => change({ messageContent: e.target.checked })} />
              <span><span className="text-slate-900">Allow message text</span>
                <span className="block text-slate-500">Needed for sentiment checks and AI alerts on customer messages.</span></span>
            </label>
          </div>
          <div className="overflow-x-auto mt-4">
            <table className="min-w-full text-sm">
              <thead><tr className="text-left text-slate-500 border-b border-slate-200">
                <th className="py-2 pr-3 font-medium">Feature</th><th className="py-2 px-3 font-medium">Sends</th><th className="py-2 px-3 font-medium">Never sends</th>
              </tr></thead>
              <tbody>
                {data.policy.map((p) => (
                  <tr key={p.feature} className="border-b border-slate-100 align-top">
                    <td className="py-2 pr-3 text-slate-800 whitespace-nowrap">{p.feature}</td>
                    <td className="py-2 px-3 text-slate-600">{p.sends}</td>
                    <td className="py-2 px-3 text-slate-500">{p.neverSends}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      {/* NFR-7.1c */}
      <AiPerformance />
    </div>
  );
};

export default AiDataSettings;
