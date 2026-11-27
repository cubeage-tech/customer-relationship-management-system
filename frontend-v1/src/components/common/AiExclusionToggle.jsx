import { useEffect, useState } from 'react';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getAiExclusion, setAiExclusion } from '../../core/services/ai.service';

/** SEC-6: keep this customer's (or lead's) data away from the AI provider — every AI feature skips it. */
const AiExclusionToggle = ({ recordType, recordId, canEdit }) => {
  const [state, setState] = useState({ id: null, excluded: false, error: '' });

  useEffect(() => {
    getAiExclusion(recordType, recordId)
      .then((r) => setState({ id: recordId, excluded: Boolean(r?.excluded), error: '' }))
      .catch((err) => setState({ id: recordId, excluded: false, error: apiErrorMessage(err, 'Could not load the AI setting.') }));
  }, [recordType, recordId]);

  if (state.id !== recordId) return null;

  const toggle = async (e) => {
    const excluded = e.target.checked;
    try {
      const r = await setAiExclusion(recordType, recordId, excluded);
      setState({ id: recordId, excluded: r.excluded, error: '' });
    } catch (err) {
      setState((s) => ({ ...s, error: apiErrorMessage(err, 'Could not save the AI setting.') }));
    }
  };

  return (
    <div className="text-sm mb-6">
      <label className="inline-flex items-start gap-2">
        <input type="checkbox" className="mt-0.5" checked={state.excluded} disabled={!canEdit} onChange={toggle} />
        <span>
          <span className="text-slate-900">Exclude from AI features</span>
          <span className="block text-xs text-slate-500">Nothing about this {recordType} is sent to the AI provider (chat, drafting, sentiment, alerts).</span>
        </span>
      </label>
      {state.error && <p className="text-xs text-red-600 mt-1">{state.error}</p>}
    </div>
  );
};

export default AiExclusionToggle;
