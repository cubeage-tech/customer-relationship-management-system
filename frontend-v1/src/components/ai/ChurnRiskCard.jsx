import { useEffect, useState } from 'react';
import { ShieldAlert } from 'lucide-react';
import Button from '../common/Button';
import { getChurnRisk } from '../../core/services/ai.service';
import { RISK_STYLE } from './churnRiskStyle';

/**
 * FR-AI-6.2: on the customer page — how likely the customer is to leave (low / medium / high,
 * 0–100) and why. Worked out in the CRM; the owner and their manager are told when it turns high.
 * Hidden for roles that can't see it.
 */
const ChurnRiskCard = ({ customerId }) => {
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getChurnRisk(customerId)
      .then((d) => !cancelled && setData(d))
      .catch(() => !cancelled && setData(null))
      .finally(() => !cancelled && setBusy(false));
    return () => {
      cancelled = true;
    };
  }, [customerId, reload]);

  if (!data) return null;

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><ShieldAlert size={17} aria-hidden="true" /> Churn risk</h2>
        <div className="flex items-center gap-2">
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${RISK_STYLE[data.level]}`}>
            {data.level === 'high' ? 'High' : data.level === 'medium' ? 'Medium' : 'Low'} · {data.score}/100
          </span>
          <Button size="sm" variant="outline" disabled={busy} onClick={() => { setBusy(true); setReload((n) => n + 1); }}>
            {busy ? 'Checking…' : 'Reassess'}
          </Button>
        </div>
      </div>
      {data.factors.length === 0 ? (
        <p className="mt-2 text-sm text-slate-500">No warning signs: no unhappy messages or reviews, open problems, buying gap or lost deals.</p>
      ) : (
        <ul className="mt-3 space-y-1 text-sm">
          {data.factors.map((f) => (
            <li key={f.name} className="flex justify-between gap-3">
              <span><span className="font-medium text-slate-800">{f.name}</span> <span className="text-slate-600">— {f.detail}</span></span>
              <span className="shrink-0 text-xs text-slate-400">+{f.points}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="mt-2 text-xs text-slate-400">
        Assessed {new Date(data.assessedAt).toLocaleString()} from the CRM's own data
        {data.level === 'high' ? ' · the owner and their manager have been told' : ''}.
      </p>
    </section>
  );
};

export default ChurnRiskCard;
