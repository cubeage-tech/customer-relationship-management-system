import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import StatCard from '../../components/common/StatCard';
import Button from '../../components/common/Button';
import BarList from '../../components/analytics/BarList';
import ReportFilters from '../../components/analytics/ReportFilters';
import ReportSchedules from '../../components/analytics/ReportSchedules';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { formatMoney, periodRange, reportParams } from '../../core/utils/reportPeriod';
import {
  exportReport,
  getCampaignReport,
  getCustomerReport,
  getRevenueReport,
  getSalesReport,
  getServiceReport,
  getTeamPerformance,
} from '../../core/services/analytics.service';

/**
 * FR-9.2–9.5, REP-SVC and REP-CMP live reports, each visible only to the roles that own them
 * (REP-SCOPE; the backend enforces the same rules and the data scope). code = the API name.
 */
const LIVE_REPORTS = [
  { key: 'sales', code: 'sales', title: 'Sales', permissions: [PERMISSIONS.REPORTS_SALES], load: getSalesReport },
  { key: 'customers', code: 'customers', title: 'Customers', permissions: [PERMISSIONS.REPORTS_SALES, PERMISSIONS.REPORTS_CAMPAIGN], load: getCustomerReport },
  { key: 'revenue', code: 'revenue', title: 'Revenue', permissions: [PERMISSIONS.REPORTS_REVENUE], load: getRevenueReport },
  { key: 'team', code: 'team-performance', title: 'Team performance', permissions: [PERMISSIONS.REPORTS_SALES], load: getTeamPerformance },
  { key: 'service', code: 'service', title: 'Service', permissions: [PERMISSIONS.REPORTS_SERVICE], load: getServiceReport },
  { key: 'campaigns', code: 'campaigns', title: 'Campaigns', permissions: [PERMISSIONS.REPORTS_CAMPAIGN], load: getCampaignReport },
];

/** Which filters each report takes; the rest are left out of the request. */
const SALES_FILTERS = ['from', 'to', 'industry', 'teamId', 'userId'];
const FILTERS_FOR = { service: ['from', 'to', 'priority', 'technicianId'], campaigns: ['from', 'to'] };

const th = 'text-left font-medium text-slate-500 px-3 py-2 whitespace-nowrap';
const td = 'px-3 py-2 text-slate-700 whitespace-nowrap';
const pct = (v) => (v == null ? '—' : `${v}%`);
const num = (v) => (v == null ? '—' : v);
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

const Table = ({ title, head, rows, empty = 'Nothing in this period.' }) => (
  <div className="bg-white border border-slate-200 rounded-lg p-5">
    <h3 className="font-semibold text-slate-900 mb-3">{title}</h3>
    {rows.length === 0 ? (
      <p className="text-sm text-slate-400">{empty}</p>
    ) : (
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead><tr className="border-b border-slate-200">{head.map((h) => <th key={h} className={th}>{h}</th>)}</tr></thead>
          <tbody>{rows}</tbody>
        </table>
      </div>
    )}
  </div>
);

const SalesView = ({ data, drill }) => (
  <div className="space-y-6">
    <div className="grid gap-4 sm:grid-cols-2">
      <StatCard label="Opportunities created" value={data.totalCount} />
      <StatCard label="Total value" value={formatMoney(data.totalValue)} />
    </div>
    <BarList title="By stage" rows={data.byStage} onSelect={(r) => drill('stage', r)} selectHint="Open these opportunities" />
    <Table
      title="By owner"
      head={['Owner', 'Open', 'Open value', 'Won', 'Won value', 'Lost', 'Lost value', 'Total value']}
      rows={data.byOwner.map((o) => (
        <tr key={o.ownerId ?? 'none'} className="border-b border-slate-100">
          <td className={td}>{o.ownerName}</td><td className={td}>{o.openCount}</td><td className={td}>{formatMoney(o.openValue)}</td>
          <td className={td}>{o.wonCount}</td><td className={td}>{formatMoney(o.wonValue)}</td>
          <td className={td}>{o.lostCount}</td><td className={td}>{formatMoney(o.lostValue)}</td><td className={td}>{formatMoney(o.totalValue)}</td>
        </tr>
      ))}
    />
  </div>
);

const CustomersView = ({ data, drill }) => (
  <div className="space-y-6">
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard label="Active customers" value={data.activeCustomers} />
      <StatCard label="New in period" value={data.newCustomers} />
      <StatCard label="Contacted in period" value={data.contactedCustomers} />
      <StatCard label="Not contacted" value={data.notContactedCustomers} />
    </div>
    <div className="grid gap-6 lg:grid-cols-2">
      <BarList title="Active customers by industry" rows={data.byIndustry} measure="count"
        onSelect={(r) => drill('industry', r)} selectHint="Open these customers" />
      <BarList title="Activity by channel" rows={data.activityByChannel} measure="count" />
    </div>
    {data.segments.length > 0 && <BarList title="Marketing segments" rows={data.segments} measure="count" />}
  </div>
);

const RevenueView = ({ data, drill }) => (
  <div className="space-y-6">
    <div className="grid gap-4 sm:grid-cols-2">
      <StatCard label="Revenue (won deals)" value={formatMoney(data.totalRevenue)} />
      <StatCard label="Deals won" value={data.wonDeals} />
    </div>
    <div className="grid gap-6 lg:grid-cols-2">
      <BarList title="By month" rows={data.byMonth} onSelect={(r) => drill('month', r)} selectHint="Show just this month" />
      <BarList title="By salesperson" rows={data.bySalesperson} onSelect={(r) => drill('user', r)} selectHint="Show just this salesperson" />
      <BarList title="By product / service" rows={data.byProduct} />
      <BarList title="By industry" rows={data.byIndustry} onSelect={(r) => drill('industry', r)} selectHint="Open these customers" />
    </div>
  </div>
);

const TeamView = ({ data, drill }) => (
  <div className="space-y-6">
    <Table
      title="Salespeople"
      head={['Name', 'Team', 'Revenue', 'Won', 'Target', 'Attainment', 'Calls', 'Meetings', 'Quotations sent', 'Visits', 'Commission']}
      rows={data.people.map((p) => (
        <tr key={p.userId} className="border-b border-slate-100">
          <td className={td}>
            <button type="button" className="text-primary hover:underline" onClick={() => drill('user', { key: String(p.userId) })}>{p.name}</button>
          </td><td className={td}>{p.teamName ?? '—'}</td><td className={td}>{formatMoney(p.revenue)}</td>
          <td className={td}>{p.wonDeals}</td><td className={td}>{formatMoney(p.targetValue)}</td><td className={td}>{pct(p.attainmentPercent)}</td>
          <td className={td}>{p.calls}</td><td className={td}>{p.meetings}</td><td className={td}>{p.quotationsSent}</td><td className={td}>{p.visits}</td>
          <td className={td}>{formatMoney(p.commission)}</td>
        </tr>
      ))}
    />
    <Table
      title="Teams"
      head={['Team', 'Members', 'Revenue', 'Won', 'Target', 'Attainment', 'Commission']}
      empty="No teams in scope."
      rows={data.teams.map((t) => (
        <tr key={t.teamId} className="border-b border-slate-100">
          <td className={td}>{t.name}</td><td className={td}>{t.members}</td><td className={td}>{formatMoney(t.revenue)}</td>
          <td className={td}>{t.wonDeals}</td><td className={td}>{formatMoney(t.targetValue)}</td><td className={td}>{pct(t.attainmentPercent)}</td>
          <td className={td}>{formatMoney(t.commission)}</td>
        </tr>
      ))}
    />
    <p className="text-xs text-slate-400">Attainment covers revenue targets overlapping the period, each measured over its own target period.</p>
  </div>
);

const serviceRows = (rows, onPick) => rows.map((r) => (
  <tr key={r.key ?? 'none'} className="border-b border-slate-100">
    <td className={td}>
      {onPick && r.key && r.key !== 'null'
        ? <button type="button" className="text-primary hover:underline" onClick={() => onPick(r)}>{r.label}</button>
        : r.label}
    </td><td className={td}>{r.created}</td><td className={td}>{r.resolved}</td>
    <td className={td}>{num(r.avgResolutionHours)}</td><td className={td}>{pct(r.slaCompliancePercent)}</td><td className={td}>{num(r.averageFeedback)}</td>
  </tr>
));
const SERVICE_HEAD = ['', 'Raised', 'Resolved', 'Avg resolution (h)', 'SLA compliance', 'Avg feedback'];

const ServiceView = ({ data, drill }) => (
  <div className="space-y-6">
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard label="Tickets raised" value={data.created} hint={`${data.resolved} resolved`} />
      <StatCard label="Avg resolution time" value={data.avgResolutionHours == null ? '—' : `${data.avgResolutionHours} h`} />
      <StatCard label="SLA compliance" value={pct(data.slaCompliancePercent)} hint={`${data.resolvedWithinSla} within SLA · ${data.openBreached} open past SLA now`} />
      <StatCard label="Feedback score" value={data.averageFeedback == null ? '—' : `${data.averageFeedback} / 5`} hint={`${data.feedbackCount} ratings`} />
    </div>
    <Table title="By priority" head={SERVICE_HEAD} rows={serviceRows(data.byPriority, (r) => drill('priority', r))} />
    <Table title="By technician" head={SERVICE_HEAD} rows={serviceRows(data.byTechnician, (r) => drill('technician', r))} />
  </div>
);

const CampaignsView = ({ data }) => (
  <div className="space-y-6">
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
      <StatCard label="Sent" value={data.sent} />
      <StatCard label="Opened" value={data.opened} />
      <StatCard label="Clicked" value={data.clicked} />
      <StatCard label="Converted" value={data.converted} />
      <StatCard label="Leads generated" value={data.leadsGenerated} />
    </div>
    <Table
      title="Campaigns running in the period"
      head={['Campaign', 'Channel', 'Status', 'Sent', 'Opened', 'Clicked', 'Converted', 'Open rate', 'Click rate', 'Conversion', 'Leads', 'Cost per lead']}
      empty="No campaigns ran in this period."
      rows={data.campaigns.map((c) => (
        <tr key={c.campaignId} className="border-b border-slate-100">
          <td className={td}>{c.name}</td><td className={td}>{c.channel}</td><td className={td}>{c.status}</td>
          <td className={td}>{c.sent}</td><td className={td}>{c.opened}</td><td className={td}>{c.clicked}</td><td className={td}>{c.converted}</td>
          <td className={td}>{pct(c.openRate)}</td><td className={td}>{pct(c.clickRate)}</td><td className={td}>{pct(c.conversionRate)}</td>
          <td className={td}>{c.leadsGenerated}</td><td className={td}>{formatMoney(c.costPerLead)}</td>
        </tr>
      ))}
    />
    <p className="text-xs text-slate-400">Converted = recipients who won a deal or approved a quotation within 30 days of the send.</p>
  </div>
);

const VIEWS = { sales: SalesView, customers: CustomersView, revenue: RevenueView, team: TeamView, service: ServiceView, campaigns: CampaignsView };

const Reports = () => {
  const { can, scopeFor } = usePermissions();
  const live = LIVE_REPORTS.filter((r) => r.permissions.some((p) => can(p)));

  const [params] = useSearchParams();
  const navigate = useNavigate();
  // UI-7/UI-8: dashboard report links and chart drill-downs open a tab with its filters.
  const [tab, setTab] = useState(() => (live.some((r) => r.key === params.get('tab')) ? params.get('tab') : live[0]?.key));
  const [filters, setFilters] = useState(() => {
    const base = { ...periodRange('month'), industry: '', teamId: '', userId: '', priority: '', technicianId: '' };
    Object.keys(base).forEach((k) => { if (params.get(k)) base[k] = params.get(k); });
    return base;
  });

  /** UI-8 drill-down: narrow this report, or open the matching records. */
  const drill = (kind, row) => {
    if (kind === 'stage') navigate(`/opportunities?stage=${encodeURIComponent(row.key)}`);
    else if (kind === 'industry') navigate(`/customers?industry=${encodeURIComponent(row.key)}`);
    else if (kind === 'user') setFilters((f) => ({ ...f, userId: row.key }));
    else if (kind === 'priority') setFilters((f) => ({ ...f, priority: row.key }));
    else if (kind === 'technician') setFilters((f) => ({ ...f, technicianId: row.key }));
    else if (kind === 'month') {
      const [y, m] = row.key.split('-').map(Number);
      const last = new Date(y, m, 0).getDate();
      setFilters((f) => ({ ...f, from: `${row.key}-01`, to: `${row.key}-${String(last).padStart(2, '0')}` }));
    }
  };
  const [result, setResult] = useState({ key: null, data: null, error: '' });
  const [technicians, setTechnicians] = useState([]);
  const [exportError, setExportError] = useState('');

  const report = live.find((r) => r.key === tab);
  const keys = FILTERS_FOR[tab] ?? SALES_FILTERS;
  const params = reportParams(Object.fromEntries(keys.map((k) => [k, filters[k]])));
  const requestKey = report ? JSON.stringify([tab, params]) : null;

  useEffect(() => {
    if (!report) return;
    const key = JSON.stringify([report.key, params]);
    report.load(params)
      .then((data) => {
        setResult({ key, data, error: '' });
        // Keep every technician seen so far, so picking one doesn't empty the list.
        if (report.key === 'service') {
          setTechnicians((prev) => {
            const seen = new Map(prev.map((t) => [t.key, t]));
            data.byTechnician.filter((t) => t.key && t.key !== 'null').forEach((t) => seen.set(t.key, t));
            return [...seen.values()];
          });
        }
      })
      .catch((err) => setResult({ key, data: null, error: apiErrorMessage(err, 'Could not load the report.') }));
    // params is derived from report + filters; requestKey captures it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey]);

  const download = async (format) => {
    setExportError('');
    try {
      await exportReport(report.code, format, params);
    } catch (err) {
      setExportError(apiErrorMessage(err, 'Could not export the report.'));
    }
  };

  const loaded = result.key === requestKey;
  const View = VIEWS[tab];

  return (
    <section>
      <PageHeader
        title="Reports"
        subtitle={`Reporting available to your role — ${SCOPE_LABELS[scopeFor(MODULES.REPORTS)]}`}
      />

      {live.length === 0 && (
        <EmptyState
          title="No reports available"
          message="Your role has access to dashboards only. Ask your tenant administrator if you need reporting access."
        />
      )}

      {live.length > 0 && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <div className="flex flex-wrap gap-2" role="tablist">
              {live.map((r) => (
                <button
                  key={r.key}
                  type="button"
                  role="tab"
                  aria-selected={tab === r.key}
                  onClick={() => setTab(r.key)}
                  className={`px-3 py-1.5 rounded-md text-sm border ${tab === r.key
                    ? 'border-primary/50 bg-primary/5 text-primary'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-primary/50'}`}
                >
                  {r.title}
                </button>
              ))}
            </div>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={() => download('pdf')}>Export PDF</Button>
              <Button size="sm" variant="outline" onClick={() => download('xlsx')}>Export Excel</Button>
            </div>
          </div>
          {exportError && <p className="text-sm text-red-600 mb-3">{exportError}</p>}

          <ReportFilters value={filters} onChange={setFilters} salesFilters={!FILTERS_FOR[tab]}>
            {tab === 'service' && (
              <>
                <label className="flex flex-col gap-1">
                  <span className="text-slate-500">Priority</span>
                  <select value={filters.priority} onChange={(e) => setFilters({ ...filters, priority: e.target.value })} className={inputClass}>
                    <option value="">All priorities</option>
                    {['low', 'medium', 'high', 'critical'].map((p) => <option key={p} value={p}>{p[0].toUpperCase() + p.slice(1)}</option>)}
                  </select>
                </label>
                {technicians.length > 1 && (
                  <label className="flex flex-col gap-1">
                    <span className="text-slate-500">Technician</span>
                    <select value={filters.technicianId} onChange={(e) => setFilters({ ...filters, technicianId: e.target.value })} className={inputClass}>
                      <option value="">Everyone</option>
                      {technicians.map((t) => <option key={t.key} value={t.key}>{t.label}</option>)}
                    </select>
                  </label>
                )}
              </>
            )}
          </ReportFilters>

          {!loaded && <p className="text-sm text-slate-400">Loading…</p>}
          {loaded && result.error && <p className="text-sm text-red-600">{result.error}</p>}
          {loaded && result.data && (
            <>
              <View data={result.data} drill={drill} />
              <p className="text-xs text-slate-400 mt-4">
                {result.data.from} to {result.data.to} · generated {new Date(result.data.generatedAt).toLocaleString()}
              </p>
            </>
          )}

          <ReportSchedules reports={live.map((r) => ({ code: r.code, title: `${r.title} report` }))} filters={filters} />
        </>
      )}
    </section>
  );
};

export default Reports;
