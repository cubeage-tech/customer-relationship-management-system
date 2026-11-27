import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { acknowledgeCoaching, listSharedCoaching } from '../../core/services/ai.service';

const day = (d) => new Date(`${d}T00:00:00`).toLocaleDateString();

/**
 * FR-AI-8.3: coaching shared between this salesperson and their manager — newest first, with the
 * sharer's note; the salesperson acknowledges each with an optional reply. {@code version} changes
 * when a new plan is shared.
 */
const CoachingHistory = ({ userId, isMe, version }) => {
  const [items, setItems] = useState([]);
  const [replies, setReplies] = useState({});
  const [open, setOpen] = useState(null);
  const [error, setError] = useState('');

  const load = () => listSharedCoaching(userId).then((d) => setItems(d ?? [])).catch((err) => setError(apiErrorMessage(err, 'Could not load shared coaching.')));

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, version]);

  const acknowledge = async (c) => {
    setError('');
    try {
      await acknowledgeCoaching(c.id, (replies[c.id] || '').trim() || undefined);
      load();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not acknowledge the coaching.'));
    }
  };

  if (items.length === 0 && !error) return null;

  return (
    <div className="mt-3 border-t border-slate-100 pt-3 text-sm">
      <h4 className="font-medium text-slate-800">Shared coaching</h4>
      {error && <p className="text-red-600">{error}</p>}
      <ul className="mt-1 space-y-2">
        {items.map((c) => (
          <li key={c.id} className="rounded-md border border-slate-200 p-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-slate-700">
                {c.sharedById === c.userId ? 'Shared by them with their manager' : `From ${c.sharedByName || '—'}`} · {new Date(c.sharedAt).toLocaleDateString()}
                <span className="text-xs text-slate-400"> · for {day(c.from)}–{day(c.to)}</span>
              </span>
              <span className="flex items-center gap-3 text-xs">
                {c.acknowledgedAt ? <span className="text-emerald-700">Acknowledged {new Date(c.acknowledgedAt).toLocaleDateString()}</span>
                  : <span className="text-amber-700">Not yet acknowledged</span>}
                <button type="button" className="text-primary hover:underline" onClick={() => setOpen((v) => (v === c.id ? null : c.id))}>
                  {open === c.id ? 'Hide' : 'View'}
                </button>
              </span>
            </div>
            {c.note && <p className="mt-1 text-slate-700">“{c.note}”</p>}
            {c.reply && <p className="mt-1 text-xs text-slate-600">Reply: {c.reply}</p>}
            {open === c.id && (
              <div className="mt-2 space-y-1 text-slate-700">
                <p>{c.plan.summary}</p>
                {c.plan.focusAreas.map((a) => (
                  <div key={a.area}>
                    <p className="font-medium">{a.area}{a.measure ? <span className="font-normal text-xs text-emerald-700"> — success: {a.measure}</span> : null}</p>
                    <ul className="list-disc pl-5">{a.actions.map((x) => <li key={x}>{x}</li>)}</ul>
                  </div>
                ))}
                {c.plan.weeklyGoals.length > 0 && <p className="text-xs">Goals: {c.plan.weeklyGoals.join(' · ')}</p>}
              </div>
            )}
            {isMe && !c.acknowledgedAt && c.sharedById !== c.userId && (
              <div className="mt-2 flex flex-wrap gap-2">
                <input value={replies[c.id] || ''} onChange={(e) => setReplies((r) => ({ ...r, [c.id]: e.target.value }))} maxLength={1000}
                  placeholder="Reply (optional)" className="min-w-48 flex-1 rounded border border-slate-300 px-2 py-1" aria-label="Reply" />
                <Button size="sm" onClick={() => acknowledge(c)}>Acknowledge</Button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default CoachingHistory;
