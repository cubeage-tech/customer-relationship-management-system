import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import BarList from './BarList';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getCustomerReport, getRevenueReport, getSalesReport, getServiceReport } from '../../core/services/analytics.service';

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const monthsBack = (n) => {
  const today = new Date();
  return { from: iso(new Date(today.getFullYear(), today.getMonth() - n + 1, 1)), to: iso(today) };
};
const PRESETS = [{ months: 3, label: '3 months' }, { months: 6, label: '6 months' }, { months: 12, label: '12 months' }];
const monthEnd = (key) => {
  const [y, m] = key.split('-').map(Number);
  return `${key}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}`;
};
const qs = (o) => new URLSearchParams(Object.entries(o).filter(([, v]) => v)).toString();

/** One chart per role, picked by the first report the user may see; every bar drills down (UI-8). */
const CHARTS = [
  { permission: PERMISSIONS.REPORTS_REVENUE, title: 'Revenue by month', load: getRevenueReport, rows: (d) => d.byMonth,
    hint: 'Open this month in the revenue report', to: (r) => `/reports?${qs({ tab: 'revenue', from: `${r.key}-01`, to: monthEnd(r.key) })}` },
  { permission: PERMISSIONS.REPORTS_SALES, title: 'Opportunities by stage', load: getSalesReport, rows: (d) => d.byStage,
    hint: 'Open these opportunities', to: (r) => `/opportunities?${qs({ stage: r.key })}` },
  { permission: PERMISSIONS.REPORTS_CAMPAIGN, title: 'Active customers by industry', load: getCustomerReport, rows: (d) => d.byIndustry, measure: 'count',
    hint: 'Open these customers', to: (r) => `/customers?${qs({ industry: r.key })}` },
  { permission: PERMISSIONS.REPORTS_SERVICE, title: 'Tickets by priority', load: getServiceReport, rows: (d) => d.byPriority, measure: 'count',
    hint: 'Open this priority in the service report', to: (r, range) => `/reports?${qs({ tab: 'service', priority: r.key, ...range })}` },
];

const REPORT_LINKS = [
  { tab: 'sales', label: 'Sales', permissions: [PERMISSIONS.REPORTS_SALES] },
  { tab: 'customers', label: 'Customers', permissions: [PERMISSIONS.REPORTS_SALES, PERMISSIONS.REPORTS_CAMPAIGN] },
  { tab: 'revenue', label: 'Revenue', permissions: [PERMISSIONS.REPORTS_REVENUE] },
  { tab: 'team', label: 'Team performance', permissions: [PERMISSIONS.REPORTS_SALES] },
  { tab: 'service', label: 'Service', permissions: [PERMISSIONS.REPORTS_SERVICE] },
  { tab: 'campaigns', label: 'Campaigns', permissions: [PERMISSIONS.REPORTS_CAMPAIGN] },
];

const input = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

/**
 * UI-7 / UI-8: the dashboard's interactive chart — a date range (presets or custom), bars that open
 * the underlying records or report, and links to every report this role can open.
 */
const DashboardInsights = () => {
  const { can } = usePermissions();
  const navigate = useNavigate();
  const chart = CHARTS.find((c) => can(c.permission));
  const links = REPORT_LINKS.filter((l) => l.permissions.some((p) => can(p)));
  const [range, setRange] = useState(() => monthsBack(6));
  const [result, setResult] = useState({ key: null, rows: [], error: '' });
  const key = `${range.from}|${range.to}`;

  useEffect(() => {
    if (!chart || !range.from || !range.to || range.from > range.to) return;
    const k = `${range.from}|${range.to}`;
    chart.load(range)
      .then((d) => setResult({ key: k, rows: chart.rows(d) ?? [], error: '' }))
      .catch((err) => setResult({ key: k, rows: [], error: apiErrorMessage(err, 'Could not load the chart.') }));
  }, [chart, range]);

  if (!chart && links.length === 0) return null;
  const preset = PRESETS.find((p) => { const r = monthsBack(p.months); return r.from === range.from && r.to === range.to; });

  return (
    <div className="grid gap-4 lg:grid-cols-3 mt-4">
      {chart && (
        <div className="lg:col-span-2">
          <div className="flex flex-wrap items-end gap-2 mb-2 text-sm">
            {PRESETS.map((p) => (
              <button key={p.months} type="button" onClick={() => setRange(monthsBack(p.months))}
                className={`px-2.5 py-1 rounded border ${preset?.months === p.months ? 'bg-primary text-white border-primary' : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50'}`}>
                {p.label}
              </button>
            ))}
            <label className="flex flex-col gap-0.5 ml-auto">
              <span className="text-xs text-slate-500">From</span>
              <input type="date" value={range.from} max={range.to} onChange={(e) => setRange({ ...range, from: e.target.value })} className={input} />
            </label>
            <label className="flex flex-col gap-0.5">
              <span className="text-xs text-slate-500">To</span>
              <input type="date" value={range.to} min={range.from} onChange={(e) => setRange({ ...range, to: e.target.value })} className={input} />
            </label>
          </div>
          {result.key !== key && <p className="text-sm text-slate-400">Loading…</p>}
          {result.key === key && result.error && <p className="text-sm text-red-600">{result.error}</p>}
          {result.key === key && !result.error && (
            <BarList title={chart.title} rows={result.rows} measure={chart.measure} selectHint={chart.hint}
              onSelect={(r) => navigate(chart.to(r, range))} />
          )}
        </div>
      )}
      {links.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <h3 className="font-semibold text-slate-900 text-sm mb-2">Reports</h3>
          <ul className="space-y-1 text-sm">
            {links.map((l) => (
              <li key={l.tab}>
                <Link to={`/reports?${qs({ tab: l.tab, ...range })}`} className="text-primary hover:underline">{l.label} report</Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};

export default DashboardInsights;
