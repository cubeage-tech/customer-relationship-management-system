import { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import ScoreBadge from './ScoreBadge';
import { getLeadScores } from '../../core/services/ai.service';

const NAMES = { industry: 'Industry', source: 'Source', budget: 'Budget', buying: 'Buying behaviour', engagement: 'Engagement',
  recency: 'Recency', stage: 'Stage' };

/**
 * FR-AI-1.1/1.2: on the lead page — the AI lead score, its purchase-probability label and each
 * factor behind it (how strongly it counts and why).
 */
const LeadScoreCard = ({ leadId, reloadKey }) => {
  const [result, setResult] = useState({ key: null, score: null });
  const key = `${leadId}:${reloadKey ?? ''}`;

  useEffect(() => {
    let cancelled = false;
    getLeadScores({ leadId })
      .then((data) => !cancelled && setResult({ key, score: data?.[0] ?? null }))
      .catch(() => !cancelled && setResult({ key, score: null }));
    return () => {
      cancelled = true;
    };
  }, [leadId, key]);

  const s = result.key === key ? result.score : null;
  if (!s) return null;

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Sparkles size={17} aria-hidden="true" /> AI lead score</h2>
        <ScoreBadge value={s.score} label={s.label} />
      </div>
      <p className="mt-1 text-xs text-slate-500">
        {s.label === 'High' ? 'Likely to buy' : s.label === 'Medium' ? 'Could go either way' : 'Unlikely to buy soon'} — from your own conversion history.
      </p>
      <ul className="mt-3 space-y-2">
        {s.factors.map((f) => (
          <li key={f.name} className="text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="text-slate-700">{NAMES[f.name] || f.name} <span className="text-xs text-slate-400">({Math.round(f.weight * 100)}%)</span></span>
              <span className="h-1.5 w-24 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
                <span className="block h-full rounded-full bg-primary" style={{ width: `${Math.round(f.value * 100)}%` }} />
              </span>
            </div>
            <p className="text-xs text-slate-500">{f.detail}</p>
          </li>
        ))}
      </ul>
    </section>
  );
};

export default LeadScoreCard;
