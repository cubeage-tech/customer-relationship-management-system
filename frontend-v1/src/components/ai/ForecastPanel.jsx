import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import StatCard from '../common/StatCard';
import { apiErrorMessage } from '../../core/utils/apiError';
import { formatMoney } from '../../core/utils/reportPeriod';
import { getForecast } from '../../core/services/ai.service';

const PERIODS = { next_month: 'Next month', this_month: 'This month', next_quarter: 'Next quarter', this_quarter: 'This quarter' };
const signed = (v) => (v == null ? '—' : `${v > 0 ? '+' : ''}${v}%`);

/**
 * FR-AI-2.1/2.3: the revenue forecast — won so far + weighted open deals + new business at the recent
 * run rate, with a likely range, the six periods before it, growth on the previous period and the
 * variance from the revenue target.
 */
const ForecastPanel = () => {
  const [period, setPeriod] = useState('next_month');
  const [result, setResult] = useState({ period: null, data: null, error: '' });
  useEffect(() => {
    let cancelled = false;
    getForecast(period)
      .then((data) => !cancelled && setResult({ period, data, error: '' }))
      .catch((err) => !cancelled && setResult({ period, data: null, error: apiErrorMessage(err, 'Could not forecast.') }));
    return () => {
      cancelled = true;
    };
  }, [period]);
  const f = result.period === period ? result.data : null;
  const peak = f ? Math.max(1, ...f.history.map((h) => Number(h.won)), Number(f.expectedRevenue)) : 1;

  return (
    <div className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold text-slate-900">Revenue forecast</h2>
        <select value={period} onChange={(e) => setPeriod(e.target.value)} aria-label="Forecast period"
          className="border border-slate-300 rounded px-2 py-1 text-sm bg-white">
          {Object.entries(PERIODS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </div>
      {result.error && <p className="text-sm text-red-600 mt-2">{result.error}</p>}
      {f && (
        <>
          <div className="grid gap-3 sm:grid-cols-4 mt-3">
            <StatCard label="Expected revenue" value={formatMoney(f.expectedRevenue)}
              hint={`likely ${formatMoney(f.low)} – ${formatMoney(f.high)}`} />
            <StatCard label="vs previous period" value={signed(f.growthPercent)} hint={`${formatMoney(f.previousPeriodRevenue)} last period`} />
            <StatCard label="vs target" value={signed(f.variancePercent)}
              hint={f.target ? `target ${formatMoney(f.target)} · ${f.targetCoveragePercent}% covered` : 'no revenue target set'} />
            <StatCard label="Still needed" value={f.gapToTarget == null ? '—' : formatMoney(f.gapToTarget)}
              hint={f.gapToTarget == null ? 'set a target to compare' : f.gapToTarget > 0 ? 'to reach the target' : 'target reached'} />
          </div>
          <p className="text-xs text-slate-500 mt-2">
            {f.from} to {f.to}: won so far {formatMoney(f.wonSoFar)} + open deals weighted by their chance {formatMoney(f.weightedPipeline)}
            {' '}+ new business at the recent run rate {formatMoney(f.newBusiness)} · win rate {Math.round(f.historicalWinRate * 100)}%
          </p>

          <div className="mt-3 flex items-end gap-2 h-24" aria-label="Revenue won in earlier periods and this forecast">
            {f.history.map((h) => (
              <div key={h.from} className="flex flex-1 flex-col items-center gap-1" title={`${h.from} – ${h.to}: ${formatMoney(h.won)}`}>
                <span className="w-full rounded-t bg-slate-300" style={{ height: `${Math.max(2, (Number(h.won) / peak) * 80)}px` }} />
                <span className="text-[10px] text-slate-400">{h.from.slice(5)}</span>
              </div>
            ))}
            <div className="flex flex-1 flex-col items-center gap-1" title={`Forecast: ${formatMoney(f.expectedRevenue)}`}>
              <span className="w-full rounded-t bg-primary" style={{ height: `${Math.max(2, (Number(f.expectedRevenue) / peak) * 80)}px` }} />
              <span className="text-[10px] font-semibold text-primary">Forecast</span>
            </div>
          </div>

          {f.deals.length > 0 && (
            <ul className="mt-3 text-sm divide-y divide-slate-100">
              {f.deals.slice(0, 8).map((d) => (
                <li key={d.opportunityId} className="py-1.5 flex justify-between gap-3">
                  <Link to={`/opportunities/${d.opportunityId}`} className="hover:underline truncate">{d.customerName} · {d.stage}</Link>
                  <span className="text-slate-600 whitespace-nowrap">{formatMoney(d.dealValue)} × {Math.round(d.probability * 100)}%</span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
};

export default ForecastPanel;
