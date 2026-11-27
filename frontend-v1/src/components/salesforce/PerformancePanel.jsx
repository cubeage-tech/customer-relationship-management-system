import { useEffect, useState } from 'react';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getSalespersonAnalysis } from '../../core/services/ai.service';
import { formatMoney } from '../../core/utils/reportPeriod';
import CoachingCard from './CoachingCard';
import { useAuth } from '../../core/hooks/useAuth';

const iso = (d) => d.toISOString().slice(0, 10);
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm';
const pct = (v) => (v == null ? '—' : `${Math.round(v * 100)}%`);

/** A figure with the team median and the previous period under it. */
const Figure = ({ label, value, team, before }) => (
  <div className="rounded-md bg-slate-50 p-2">
    <p className="text-xs text-slate-500">{label}</p>
    <p className="text-base font-semibold text-slate-900">{value}</p>
    {(team != null || before != null) && (
      <p className="text-[11px] text-slate-500">{team != null && `team ${team}`}{team != null && before != null && ' · '}{before != null && `before ${before}`}</p>
    )}
  </div>
);

/**
 * FR-AI-8.1: each salesperson's activity and conversion over a period — against the team's median
 * and their previous period — with the strengths and gaps that stand out. Worked out in the CRM.
 */
const PerformancePanel = () => {
  const { user } = useAuth();
  const [from, setFrom] = useState(() => iso(new Date(Date.now() - 89 * 86400000)));
  const [to, setTo] = useState(() => iso(new Date()));
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getSalespersonAnalysis({ from, to })
      .then((d) => { if (!cancelled) { setData(d); setError(''); } })
      .catch((err) => !cancelled && setError(apiErrorMessage(err, 'Could not analyse performance.')));
    return () => {
      cancelled = true;
    };
  }, [from, to]);

  const t = data?.team;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <label>From <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className={inputClass} /></label>
        <label>to <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className={inputClass} /></label>
        {t && <span className="text-xs text-slate-500">Compared with the median of {t.people} salespeople and the previous period of the same length.</span>}
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {data && data.people.length === 0 && <p className="text-sm text-slate-500">No salespeople to show.</p>}

      {data?.people.map((p) => {
        const m = p.metrics;
        const b = p.previous;
        return (
          <section key={p.userId} className="rounded-lg border border-slate-200 bg-white p-5">
            <h3 className="font-semibold text-slate-900">{p.name} <span className="text-xs font-normal text-slate-500">{p.role.replace('_', ' ')}</span></h3>

            <div className="mt-3 grid gap-2 sm:grid-cols-3 lg:grid-cols-6">
              <Figure label="Customer touches" value={m.touches} team={t.touches} before={b?.touches} />
              <Figure label="Calls (connected)" value={`${m.calls} (${m.connectedCalls})`} team={pct(t.callConnectRate)} />
              <Figure label="Meetings · visits" value={`${m.meetings} · ${m.visits}`} />
              <Figure label="Messages sent" value={m.messagesSent} />
              <Figure label="Quotations sent" value={m.quotationsSent} />
              <Figure label="Tasks done (overdue)" value={`${m.tasksDone} (${m.tasksOverdue})`} />
              <Figure label="Leads converted" value={`${m.leadsConverted} of ${m.leadsReceived}`} team={pct(t.leadConversionRate)} />
              <Figure label="Win rate" value={`${pct(m.winRate)} (${m.dealsWon}/${m.dealsWon + m.dealsLost})`} team={pct(t.winRate)} before={b ? pct(b.winRate) : null} />
              <Figure label="Revenue won" value={formatMoney(m.revenueWon)} team={formatMoney(t.revenueWon)} before={b ? formatMoney(b.revenueWon) : null} />
              <Figure label="Average deal" value={formatMoney(m.averageDeal)} />
              <Figure label="Days to win" value={m.daysToClose ?? '—'} team={t.daysToClose ?? null} />
              <Figure label="Quotes accepted" value={pct(m.quoteAcceptance)} team={pct(t.quoteAcceptance)} />
            </div>
            <p className="mt-2 text-xs text-slate-500">
              Open pipeline {formatMoney(m.openPipeline)}
              {m.targetAttainment != null && ` · revenue target ${pct(m.targetAttainment)} reached with ${pct(m.targetPace)} of its period gone`}
            </p>

            {(p.strengths.length > 0 || p.gaps.length > 0) ? (
              <div className="mt-3 grid gap-3 sm:grid-cols-2 text-sm">
                <div>
                  <h4 className="text-xs font-semibold uppercase text-emerald-700">Strengths</h4>
                  {p.strengths.length === 0 ? <p className="text-slate-500">None stand out yet.</p>
                    : <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-700">{p.strengths.map((f) => <li key={f.metric}>{f.text}</li>)}</ul>}
                </div>
                <div>
                  <h4 className="text-xs font-semibold uppercase text-red-700">To work on</h4>
                  {p.gaps.length === 0 ? <p className="text-slate-500">Nothing stands out.</p>
                    : <ul className="mt-1 list-disc space-y-0.5 pl-5 text-slate-700">{p.gaps.map((f) => <li key={f.metric}>{f.text}</li>)}</ul>}
                </div>
              </div>
            ) : (
              <p className="mt-3 text-sm text-slate-500">In line with the team — nothing stands out for this period.</p>
            )}
            {/* FR-AI-8.2 */}
            <CoachingCard key={`${p.userId}:${from}:${to}`} userId={p.userId} name={p.name} from={from} to={to} isMe={user?.id === p.userId} />
          </section>
        );
      })}
    </div>
  );
};

export default PerformancePanel;
