import { useState } from 'react';
import { GraduationCap } from 'lucide-react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getSalesCoaching, shareCoaching } from '../../core/services/ai.service';
import CoachingHistory from './CoachingHistory';

/**
 * FR-AI-8.2: personal coaching for one salesperson over the chosen period — focus areas (why, what to
 * do, how to tell it worked), strengths to keep and next week's goals. From their own figures; written
 * by the AI when it is set up, otherwise by built-in rules.
 * FR-AI-8.3: shared with the salesperson (or, by the salesperson, with their manager), with a note.
 */
const CoachingCard = ({ userId, name, from, to, isMe }) => {
  const [plan, setPlan] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [note, setNote] = useState('');
  const [shared, setShared] = useState('');
  const [version, setVersion] = useState(0);

  const load = async () => {
    setBusy(true);
    setError('');
    try {
      setPlan(await getSalesCoaching({ userId, from, to }));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not prepare the coaching.'));
    } finally {
      setBusy(false);
    }
  };

  const share = async () => {
    setBusy(true);
    setError('');
    try {
      await shareCoaching({
        userId, from: plan.from, to: plan.to, summary: plan.summary, focusAreas: plan.focusAreas, strengthsToKeep: plan.strengthsToKeep,
        weeklyGoals: plan.weeklyGoals, source: plan.source, note: note.trim() || undefined,
      });
      setShared(isMe ? 'Shared with your manager.' : `Shared with ${name}.`);
      setNote('');
      setVersion((v) => v + 1);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not share the coaching.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mt-3 border-t border-slate-100 pt-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="flex items-center gap-2 font-medium text-slate-800"><GraduationCap size={16} aria-hidden="true" /> Coaching</h4>
        <Button size="sm" variant="outline" disabled={busy} onClick={load}>{busy ? 'Preparing…' : plan ? 'Prepare again' : 'Get coaching'}</Button>
      </div>
      {error && <p className="mt-1 text-red-600">{error}</p>}
      {plan && (
        <div className="mt-2 space-y-3">
          <p className="text-slate-800">{plan.summary}</p>
          {plan.focusAreas.map((a) => (
            <div key={a.area} className="rounded-md border border-slate-200 p-3">
              <p className="font-medium text-slate-900">{a.area}</p>
              {a.why && <p className="text-xs text-slate-600">{a.why}</p>}
              <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-700">{a.actions.map((x) => <li key={x}>{x}</li>)}</ul>
              {a.measure && <p className="mt-1 text-xs text-emerald-700">Success looks like: {a.measure}</p>}
            </div>
          ))}
          <div className="grid gap-3 sm:grid-cols-2">
            {plan.strengthsToKeep.length > 0 && (
              <div>
                <h5 className="text-xs font-semibold uppercase text-slate-500">Keep doing</h5>
                <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-700">{plan.strengthsToKeep.map((x) => <li key={x}>{x}</li>)}</ul>
              </div>
            )}
            {plan.weeklyGoals.length > 0 && (
              <div>
                <h5 className="text-xs font-semibold uppercase text-slate-500">Goals for next week</h5>
                <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-700">{plan.weeklyGoals.map((x) => <li key={x}>{x}</li>)}</ul>
              </div>
            )}
          </div>
          <p className="text-xs text-slate-400">
            {plan.source === 'ai' ? 'Written by AI from these figures' : 'Built-in coaching from these figures'} · {new Date(plan.generatedAt).toLocaleString()}
          </p>
          {/* FR-AI-8.3 */}
          <div className="flex flex-wrap items-center gap-2">
            <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={1000} className="min-w-48 flex-1 rounded border border-slate-300 px-2 py-1"
              placeholder={isMe ? 'Note for your manager (optional)' : `Note for ${name} (optional)`} aria-label="Note" />
            <Button size="sm" disabled={busy} onClick={share}>{isMe ? 'Share with my manager' : `Share with ${name}`}</Button>
          </div>
          {shared && <p className="text-xs text-emerald-700">{shared}</p>}
        </div>
      )}
      <CoachingHistory userId={userId} isMe={isMe} version={version} />
    </div>
  );
};

export default CoachingCard;
