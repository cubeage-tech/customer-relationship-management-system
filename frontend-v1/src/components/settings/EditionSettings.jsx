import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { applyEdition, getEditions } from '../../core/services/admin.service';

/**
 * BIZ-2: the industry edition. Choosing one adds that industry's custom fields and pipeline stages;
 * nothing is removed, so switching (or adding a second edition's fields) is safe.
 */
const EditionSettings = () => {
  const [data, setData] = useState(null);
  const [choice, setChoice] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getEditions().then((d) => { setData(d); setChoice(d?.current ?? 'standard'); })
      .catch((err) => setError(apiErrorMessage(err, 'Could not load the editions.')));
  }, []);

  const apply = async () => {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const res = await applyEdition(choice);
      setData((d) => ({ ...d, current: res.edition }));
      setMessage(`Applied: ${res.fieldsAdded} field(s) and ${res.stagesAdded} pipeline stage(s) added.`);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not apply the edition.'));
    } finally {
      setBusy(false);
    }
  };

  const selected = data?.editions.find((e) => e.key === choice);

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Industry edition</h2>
      <p className="text-sm text-slate-500 mt-1">Set the CRM up for your industry. Adds fields and pipeline stages; removes nothing.</p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      {message && <p className="text-sm text-green-700 mt-3">{message}</p>}
      {data && (
        <div className="mt-3 space-y-2 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <select value={choice} onChange={(e) => setChoice(e.target.value)} aria-label="Edition"
              className="border border-slate-300 rounded px-2 py-1 bg-white">
              {data.editions.map((e) => <option key={e.key} value={e.key}>{e.label}{e.key === data.current ? ' (current)' : ''}</option>)}
            </select>
            <Button size="sm" disabled={busy} onClick={apply}>{choice === data.current ? 'Re-apply' : 'Apply'}</Button>
          </div>
          {selected && <p className="text-slate-500">{selected.description}</p>}
        </div>
      )}
    </div>
  );
};

export default EditionSettings;
