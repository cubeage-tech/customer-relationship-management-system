import { useEffect, useState } from 'react';
import { TrendingDown, TrendingUp, Minus } from 'lucide-react';
import { getSentimentTrend } from '../../core/services/ai.service';

const PERIODS = [6, 12];
const DIRECTION = {
  improving: { label: 'Improving', Icon: TrendingUp, className: 'text-emerald-700' },
  declining: { label: 'Declining', Icon: TrendingDown, className: 'text-red-700' },
  steady: { label: 'Steady', Icon: Minus, className: 'text-slate-600' },
};

const monthLabel = (ym) => new Date(`${ym}-01T00:00:00`).toLocaleDateString(undefined, { month: 'short' });

/**
 * FR-AI-6.3: on the customer page — how the customer's messages and reviews have read month by month
 * (positive / neutral / negative, stacked) and whether it is improving or declining. From the stored
 * AI readings (FR-AI-6.1); hidden for roles that can't see it.
 */
const SentimentTrendCard = ({ customerId }) => {
  const [months, setMonths] = useState(6);
  const [data, setData] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getSentimentTrend(customerId, months).then((d) => !cancelled && setData(d)).catch(() => !cancelled && setData(null));
    return () => {
      cancelled = true;
    };
  }, [customerId, months]);

  if (!data) return null;
  const max = Math.max(1, ...data.series.map((m) => m.positive + m.neutral + m.negative));
  const dir = DIRECTION[data.direction];

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold text-slate-900">Sentiment trend</h2>
        <div className="flex items-center gap-3 text-sm">
          {dir && (
            <span className={`flex items-center gap-1 font-medium ${dir.className}`}>
              <dir.Icon size={16} aria-hidden="true" /> {dir.label}
            </span>
          )}
          <label className="sr-only" htmlFor="sentiment-trend-period">Period</label>
          <select id="sentiment-trend-period" value={months} onChange={(e) => setMonths(Number(e.target.value))}
            className="rounded border border-slate-300 px-2 py-1">
            {PERIODS.map((p) => <option key={p} value={p}>Last {p} months</option>)}
          </select>
        </div>
      </div>
      <p className="mt-1 text-sm text-slate-600">{data.summary}</p>

      {data.readings > 0 && (
        <>
          <div className="mt-4 flex h-32 items-end gap-1" role="img"
            aria-label={data.series.map((m) => `${m.month}: ${m.positive} positive, ${m.neutral} neutral, ${m.negative} negative`).join('; ')}>
            {data.series.map((m) => {
              const total = m.positive + m.neutral + m.negative;
              return (
                <div key={m.month} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1"
                  title={`${m.month}: ${m.positive} positive, ${m.neutral} neutral, ${m.negative} negative${m.averageScore != null ? ` · average ${m.averageScore}` : ''}`}>
                  <div className="flex w-full max-w-10 flex-col-reverse overflow-hidden rounded-sm" style={{ height: `${(total / max) * 100}%` }}>
                    <div className="bg-emerald-400" style={{ flexGrow: m.positive }} />
                    <div className="bg-slate-300" style={{ flexGrow: m.neutral }} />
                    <div className="bg-red-400" style={{ flexGrow: m.negative }} />
                  </div>
                </div>
              );
            })}
          </div>
          <div className="mt-1 flex gap-1 text-center text-[11px] text-slate-500" aria-hidden="true">
            {data.series.map((m) => <span key={m.month} className="min-w-0 flex-1 truncate">{monthLabel(m.month)}</span>)}
          </div>
          <div className="mt-2 flex flex-wrap gap-3 text-xs text-slate-500">
            <span className="flex items-center gap-1"><span className="size-2.5 rounded-sm bg-emerald-400" /> Positive</span>
            <span className="flex items-center gap-1"><span className="size-2.5 rounded-sm bg-slate-300" /> Neutral</span>
            <span className="flex items-center gap-1"><span className="size-2.5 rounded-sm bg-red-400" /> Negative</span>
            <span>· {data.fromMessages} message(s), {data.fromReviews} review(s)</span>
          </div>
        </>
      )}
      {data.unclassified > 0 && (
        <p className="mt-2 text-xs text-slate-500">
          {data.unclassified} message(s) in this period haven't been read yet — use "Check sentiment" on the timeline, or switch on automatic classification in AI settings.
        </p>
      )}
    </section>
  );
};

export default SentimentTrendCard;
