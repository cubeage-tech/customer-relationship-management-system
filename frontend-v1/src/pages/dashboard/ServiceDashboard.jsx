import { useEffect, useState } from 'react';
import DashboardShell from '../../components/dashboard/DashboardShell';
import { getSuperAdminDashboard } from '../../core/services/dashboard.service';
import {
  CalendarDays,
  Check,
  ChevronDown,
  CircleDollarSign,
  Database,
  FileText,
  HardDrive,
  MoreHorizontal,
  Server,
  Users,
  UserPlus,
} from 'lucide-react';

const DEFAULT_STATS = [
  { label: 'Total tenants', value: '—', hint: 'From the platform database', icon: Users, tone: 'violet' },
  { label: 'Active tenants', value: '—', hint: 'Currently active', icon: Check, tone: 'green' },
  { label: 'Total users', value: '—', hint: 'Across all tenants', icon: UserPlus, tone: 'violet' },
  { label: 'Revenue (this month)', value: '—', hint: 'Not available in the current billing model', icon: CircleDollarSign, tone: 'blue' },
];

const activity = [
  { icon: UserPlus, title: 'New tenant registered', detail: 'Acme Traders joined the platform', time: '2 hours ago' },
  { icon: FileText, title: 'Subscription upgraded', detail: 'Bright Retail → Professional', time: '5 hours ago' },
  { icon: Users, title: 'New user added', detail: '3 users added by Super Admin', time: '1 day ago' },
];

const health = [
  { icon: Server, name: 'API Server', detail: 'Running smoothly', uptime: '99.9% uptime' },
  { icon: Database, name: 'Database', detail: 'Healthy', uptime: '99.9% uptime' },
  { icon: HardDrive, name: 'File Storage', detail: 'Operational', uptime: '99.8% uptime' },
];

const cardClass = 'rounded-2xl border border-slate-200/80 bg-white/90 shadow-sm';

const ServiceDashboard = () => {
  const [dashboard, setDashboard] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getSuperAdminDashboard()
      .then((data) => {
        if (!cancelled) setDashboard(data);
      })
      .catch(() => {
        if (!cancelled) setError('Dashboard data could not be loaded. Please try again later.');
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const stats = dashboard ? [
    { ...DEFAULT_STATS[0], value: dashboard.totalTenants },
    { ...DEFAULT_STATS[1], value: dashboard.activeTenants },
    { ...DEFAULT_STATS[2], value: dashboard.totalUsers },
    DEFAULT_STATS[3],
  ] : DEFAULT_STATS;
  const planTotal = dashboard?.planDistribution?.reduce((total, plan) => total + plan.count, 0) || 1;
  const planColors = { starter: '#f28b3f', business: '#2479e9', enterprise: '#14a391' };
  const planRows = dashboard?.planDistribution?.map((plan) => ({
    name: plan.plan.charAt(0).toUpperCase() + plan.plan.slice(1),
    amount: `${Math.round((plan.count / planTotal) * 100)}% (${plan.count})`,
    color: planColors[plan.plan] || '#5b3ee4',
  })) || [];
  const planGradient = planRows.reduce((gradient, { amount, color }) => {
    const percentage = Number.parseInt(amount, 10);
    const start = gradient.stop;
    const stop = start + percentage;
    return { stop, segments: [...gradient.segments, `${color} ${start}% ${stop}%`] };
  }, { stop: 0, segments: [] }).segments.join(', ');
  const recentTenantRows = dashboard?.recentTenants?.map((tenant) => [
    tenant.companyName,
    tenant.plan.charAt(0).toUpperCase() + tenant.plan.slice(1),
    tenant.status === 'active' ? 'Active' : 'Inactive',
  ]) || [];

  return (
    <DashboardShell
      title="CRM Platform – Super Admin Dashboard"
      subtitle="Manage tenants, subscriptions and platform operations"
      stats={stats}
    >
      <div className="space-y-6">
        {error && <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}
        <div className="grid gap-6 xl:grid-cols-[1.35fr_1fr]">
          <section className={`${cardClass} p-5 sm:p-6`} aria-labelledby="tenant-growth-heading">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
              <h2 id="tenant-growth-heading" className="text-base font-bold text-slate-900">Tenant Growth</h2>
              <button type="button" className="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50" aria-label="Change date range">
                <CalendarDays size={14} /> Last 30 Days <ChevronDown size={14} />
              </button>
            </div>
            <div className="h-56 w-full">
              <svg viewBox="0 0 720 240" className="h-full w-full" role="img" aria-label="Tenant growth from 12 to 24 over the last 30 days" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="growth-fill" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0" stopColor="#6d4df5" stopOpacity=".25" />
                    <stop offset="1" stopColor="#6d4df5" stopOpacity=".02" />
                  </linearGradient>
                </defs>
                {[20, 65, 110, 155, 200].map((y) => <line key={y} x1="42" x2="710" y1={y} y2={y} stroke="#e8edf5" strokeWidth="1" />)}
                <path d="M42 150 L70 140 L98 146 L126 134 L154 128 L182 143 L210 124 L238 132 L266 130 L294 130 L322 113 L350 104 L378 119 L406 96 L434 100 L462 112 L490 94 L518 106 L546 90 L574 62 L602 91 L630 70 L658 68 L684 61 L710 51 L710 200 L42 200 Z" fill="url(#growth-fill)" />
                <path d="M42 150 L70 140 L98 146 L126 134 L154 128 L182 143 L210 124 L238 132 L266 130 L294 130 L322 113 L350 104 L378 119 L406 96 L434 100 L462 112 L490 94 L518 106 L546 90 L574 62 L602 91 L630 70 L658 68 L684 61 L710 51" fill="none" stroke="#5b3ee4" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                {[42, 210, 378, 546, 710].map((x, index) => <circle key={x} cx={x} cy={[150, 124, 119, 90, 51][index]} r="4" fill="#5b3ee4" stroke="white" strokeWidth="2" />)}
                <g fill="#7b8495" fontSize="11"><text x="10" y="204">0</text><text x="5" y="159">12</text><text x="5" y="114">18</text><text x="5" y="69">24</text><text x="5" y="24">30</text><text x="28" y="226">Aug 10</text><text x="192" y="226">Aug 18</text><text x="359" y="226">Aug 26</text><text x="523" y="226">Sep 03</text><text x="680" y="226">Sep 07</text></g>
              </svg>
            </div>
          </section>

          <section className={`${cardClass} p-5 sm:p-6`} aria-labelledby="plan-heading">
            <h2 id="plan-heading" className="mb-5 text-base font-bold text-slate-900">Plan Distribution</h2>
            <div className="flex flex-col items-center gap-6 sm:flex-row sm:justify-center">
              <div className="relative grid size-48 shrink-0 place-items-center rounded-full" style={{ background: `conic-gradient(${planGradient || '#dbe2ec 0 100%'})` }}>
                <div className="grid size-28 place-items-center rounded-full bg-white text-center"><strong className="text-2xl text-slate-900">{dashboard?.totalTenants ?? '—'}</strong><span className="text-xs text-slate-500">Total Tenants</span></div>
              </div>
              <div className="w-full space-y-3 text-sm">
                {planRows.map(({ name, amount, color }) => <div key={name} className="flex items-center justify-between gap-4"><span className="flex items-center gap-2 font-medium text-slate-700"><i className="size-3 rounded-[4px]" style={{ backgroundColor: color }} />{name}</span><strong className="text-slate-800">{amount}</strong></div>)}
              </div>
            </div>
          </section>
        </div>

        <div className="grid gap-6 xl:grid-cols-3">
          <section className={`${cardClass} overflow-hidden`} aria-labelledby="recent-tenants-heading">
            <PanelHeading id="recent-tenants-heading" title="Recent Tenants" />
            <div className="overflow-x-auto px-4 pb-4"><table className="w-full min-w-[360px] text-left text-xs"><thead className="text-[11px] uppercase tracking-wide text-slate-400"><tr><th className="px-2 py-2 font-semibold">Company</th><th className="px-2 py-2 font-semibold">Plan</th><th className="px-2 py-2 font-semibold">Status</th></tr></thead><tbody>{recentTenantRows.map(([company, plan, status]) => <tr key={company} className="border-t border-slate-100"><td className="px-2 py-2.5 font-semibold text-slate-700"><span className="mr-2 inline-grid size-6 place-items-center rounded-md bg-violet-100 text-violet-600"><FileText size={13} /></span>{company}</td><td className="px-2 py-2.5"><span className={`rounded-full px-2 py-1 font-semibold ${plan === 'Enterprise' ? 'bg-emerald-100 text-emerald-700' : plan === 'Starter' ? 'bg-orange-100 text-orange-700' : plan === 'Business' ? 'bg-blue-100 text-blue-700' : 'bg-violet-100 text-violet-700'}`}>{plan}</span></td><td className="px-2 py-2.5"><span className={`font-semibold ${status === 'Active' ? 'text-emerald-600' : 'text-red-500'}`}>● {status}</span></td></tr>)}</tbody></table></div>
          </section>
          <section className={`${cardClass} overflow-hidden`} aria-labelledby="activity-heading"><PanelHeading id="activity-heading" title="Recent Activity" /> <div className="px-4 pb-4">{activity.map(({ icon: Icon, title, detail, time }) => <div key={title} className="flex items-center gap-3 border-t border-slate-100 py-3"><span className="grid size-9 shrink-0 place-items-center rounded-xl bg-violet-100 text-violet-600"><Icon size={17} /></span><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-700">{title}</p><p className="truncate text-xs text-slate-500">{detail}</p></div><time className="shrink-0 text-[11px] text-slate-500">{time}</time></div>)}</div></section>
          <section className={`${cardClass} overflow-hidden`} aria-labelledby="health-heading"><PanelHeading id="health-heading" title="System Health" /> <div className="px-4 pb-4">{health.map(({ icon: Icon, name, detail, uptime }) => <div key={name} className="flex items-center gap-3 border-t border-slate-100 py-3"><span className="grid size-9 shrink-0 place-items-center rounded-xl bg-emerald-100 text-emerald-600"><Icon size={17} /></span><div className="flex-1"><p className="text-sm font-semibold text-slate-700">{name}</p><p className="text-xs text-slate-500">{detail}</p></div><span className="text-[11px] font-semibold text-slate-600">{uptime}</span></div>)}</div></section>
        </div>
      </div>
    </DashboardShell>
  );
};

const PanelHeading = ({ id, title }) => <div className="flex items-center justify-between px-5 pb-3 pt-4"><h2 id={id} className="text-base font-bold text-slate-900">{title}</h2><button type="button" className="text-xs font-bold text-violet-600 hover:text-violet-800">View All</button><MoreHorizontal size={16} className="hidden text-slate-400" aria-hidden="true" /></div>;

export default ServiceDashboard;
