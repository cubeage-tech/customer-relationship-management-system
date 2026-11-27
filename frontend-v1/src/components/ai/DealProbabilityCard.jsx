import { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import ScoreBadge from './ScoreBadge';
import { getDealProbabilities } from '../../core/services/ai.service';
import { formatMoney } from '../../core/utils/reportPeriod';

const effectText = (e) => (e === 1 ? '' : e > 1 ? `+${Math.round((e - 1) * 100)}%` : `−${Math.round((1 - e) * 100)}%`);

/**
 * FR-AI-2.2: on an open deal — the chance it closes as won, its expected value and what moved the
 * chance up or down. Hidden for closed deals and roles without forecast access.
 */
const DealProbabilityCard = ({ opportunityId, reloadKey }) => {
  const key = `${opportunityId}:${reloadKey ?? ''}`;
  const [result, setResult] = useState({ key: null, deal: null });

  useEffect(() => {
    let cancelled = false;
    getDealProbabilities({ opportunityId })
      .then((data) => !cancelled && setResult({ key, deal: data?.[0] ?? null }))
      .catch(() => !cancelled && setResult({ key, deal: null }));
    return () => {
      cancelled = true;
    };
  }, [opportunityId, key]);

  const d = result.key === key ? result.deal : null;
  if (!d) return null;

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Sparkles size={17} aria-hidden="true" /> Chance to win</h2>
        <ScoreBadge value={d.percent} suffix="%" label={d.label} />
      </div>
      <p className="mt-1 text-sm text-slate-600">Expected value {formatMoney(d.expectedValue)} of {formatMoney(d.dealValue)}</p>
      <ul className="mt-3 space-y-1 text-sm">
        {d.factors.map((f) => (
          <li key={f.name} className="flex justify-between gap-3">
            <span className="text-slate-600">{f.detail}</span>
            <span className={`whitespace-nowrap text-xs font-semibold ${f.effect > 1 ? 'text-emerald-700' : f.effect < 1 ? 'text-rose-600' : 'text-slate-400'}`}>
              {effectText(f.effect)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
};

export default DealProbabilityCard;
