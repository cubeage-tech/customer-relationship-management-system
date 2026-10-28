import { useState } from 'react';
import { MessagesSquare } from 'lucide-react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getTalkingPoints } from '../../core/services/ai.service';

/**
 * FR-AI-3.3: on a customer, lead or deal page — on request, an AI-suggested approach with talking
 * points, questions to ask and likely objections, plus the CRM facts it was based on.
 */
const TalkingPointsCard = ({ customerId, leadId, opportunityId }) => {
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [showFacts, setShowFacts] = useState(false);

  const prepare = async () => {
    setBusy(true);
    setError('');
    try {
      setData(await getTalkingPoints({ customerId, leadId, opportunityId }));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not prepare talking points.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><MessagesSquare size={17} aria-hidden="true" /> Sales approach</h2>
        <Button size="sm" variant="outline" onClick={prepare} disabled={busy}>
          {busy ? 'Preparing…' : data ? 'Prepare again' : 'Prepare talking points'}
        </Button>
      </div>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {!data && !error && <p className="mt-1 text-xs text-slate-500">AI-suggested approach for your next conversation, from what the CRM knows.</p>}
      {data && (
        <div className="mt-3 space-y-3 text-sm">
          <p className="text-slate-800">{data.approach}</p>
          {data.talkingPoints.length > 0 && (
            <div>
              <h3 className="text-xs font-semibold uppercase text-slate-500">Talking points</h3>
              <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-700">{data.talkingPoints.map((t) => <li key={t}>{t}</li>)}</ul>
            </div>
          )}
          {data.questions.length > 0 && (
            <div>
              <h3 className="text-xs font-semibold uppercase text-slate-500">Questions to ask</h3>
              <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-700">{data.questions.map((q) => <li key={q}>{q}</li>)}</ul>
            </div>
          )}
          {data.objections.length > 0 && (
            <div>
              <h3 className="text-xs font-semibold uppercase text-slate-500">Likely objections</h3>
              <ul className="mt-1 space-y-1 text-slate-700">
                {data.objections.map((o) => (
                  <li key={o.objection}><span className="font-medium">“{o.objection}”</span> — {o.response}</li>
                ))}
              </ul>
            </div>
          )}
          <button type="button" onClick={() => setShowFacts((v) => !v)} className="text-xs text-primary hover:underline">
            {showFacts ? 'Hide' : 'Show'} what this is based on
          </button>
          {showFacts && <ul className="list-disc pl-5 text-xs text-slate-500">{data.basedOn.map((f) => <li key={f}>{f}</li>)}</ul>}
          <p className="text-xs text-slate-400">AI suggestion — check it before you rely on it.</p>
        </div>
      )}
    </section>
  );
};

export default TalkingPointsCard;
