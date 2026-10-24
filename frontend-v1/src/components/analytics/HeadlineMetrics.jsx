import { useEffect, useState } from 'react';
import StatCard from '../common/StatCard';
import DashboardInsights from './DashboardInsights';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getDashboardPreferences, getHeadline, saveDashboardPreferences } from '../../core/services/analytics.service';
import { PERIODS, formatMoney, formatPercent, periodRange } from '../../core/utils/reportPeriod';

/** Every tile a user can pick, with how to read it from the headline response. */
const TILES = {
  totalLeads: { label: 'Total leads', value: (h) => h.current.totalLeads, hint: (h) => `${formatPercent(h.leadGrowthPercent)} vs previous` },
  convertedLeads: { label: 'Converted leads', value: (h) => h.current.convertedLeads, hint: (h) => `${h.previous.convertedLeads} previous period` },
  conversionRate: { label: 'Conversion rate', value: (h) => `${h.current.conversionRate}%`, hint: (h) => `${h.previous.conversionRate}% previous period` },
  revenue: { label: 'Revenue', value: (h) => formatMoney(h.current.revenue), hint: (h) => `${h.current.wonDeals} deals won` },
  revenueGrowth: { label: 'Revenue growth', value: (h) => formatPercent(h.revenueGrowthPercent), hint: (h) => `${formatMoney(h.previous.revenue)} previous period` },
  leadGrowth: { label: 'Lead growth', value: (h) => formatPercent(h.leadGrowthPercent), hint: (h) => `${h.previous.totalLeads} leads previous period` },
  wonDeals: { label: 'Deals won', value: (h) => h.current.wonDeals, hint: (h) => `${h.previous.wonDeals} previous period` },
  openPipeline: { label: 'Open pipeline', value: (h) => formatMoney(h.openPipelineValue), hint: (h) => `${h.openDeals} open deals` },
};

/**
 * FR-9.1: the configurable dashboard headline — leads, conversions, conversion rate, revenue and
 * growth against the previous period of the same length, within the user's data scope. AC-9.1: shows
 * when the figures were computed, with a refresh.
 */
const HeadlineMetrics = () => {
  const [prefs, setPrefs] = useState(null);
  const [headline, setHeadline] = useState(null);
  const [draft, setDraft] = useState(null);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    getDashboardPreferences()
      .then((p) => setPrefs(p ?? { widgets: Object.keys(TILES).slice(0, 6), period: 'month' }))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load your dashboard.')));
  }, []);

  useEffect(() => {
    if (!prefs) return;
    getHeadline(periodRange(prefs.period))
      .then(setHeadline)
      .catch((err) => setError(apiErrorMessage(err, 'Could not load the figures.')));
  }, [prefs, reload]);

  const toggle = (key) => setDraft((d) => ({
    ...d,
    widgets: d.widgets.includes(key) ? d.widgets.filter((w) => w !== key) : [...d.widgets, key],
  }));

  const save = async () => {
    setError('');
    try {
      setPrefs(await saveDashboardPreferences(draft));
      setDraft(null);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save your dashboard.'));
    }
  };

  if (!prefs) return error ? <p className="text-sm text-red-600 mb-6">{error}</p> : null;

  return (
    <div className="mb-6">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <p className="text-sm text-slate-500">
          {PERIODS.find((p) => p.value === prefs.period)?.label}
          {headline && ` · ${headline.from} to ${headline.to} · data as of ${new Date(headline.generatedAt).toLocaleString()}`}
        </p>
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" onClick={() => setReload((n) => n + 1)}>Refresh</Button>
          {!draft && <Button variant="outline" size="sm" onClick={() => setDraft(prefs)}>Customize</Button>}
        </div>
      </div>

      {draft && (
        <div className="bg-white border border-slate-200 rounded-lg p-4 mb-4 text-sm">
          <div className="flex flex-wrap gap-3 mb-3">
            {Object.entries(TILES).map(([key, tile]) => (
              <label key={key} className="flex items-center gap-1.5">
                <input type="checkbox" checked={draft.widgets.includes(key)} onChange={() => toggle(key)} />
                {tile.label}
              </label>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select value={draft.period} onChange={(e) => setDraft({ ...draft, period: e.target.value })}
              className="border border-slate-300 rounded px-2 py-1">
              {PERIODS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
            </select>
            <Button size="sm" onClick={save} disabled={draft.widgets.length === 0}>Save</Button>
            <Button size="sm" variant="ghost" onClick={() => setDraft(null)}>Cancel</Button>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-red-600 mb-3">{error}</p>}

      {headline && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {prefs.widgets.filter((key) => TILES[key]).map((key) => (
            <StatCard key={key} label={TILES[key].label} value={TILES[key].value(headline)} hint={TILES[key].hint(headline)} />
          ))}
        </div>
      )}

      <DashboardInsights />
    </div>
  );
};

export default HeadlineMetrics;
