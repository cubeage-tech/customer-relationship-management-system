import { useEffect, useState } from 'react';
import DashboardShell from '../../components/dashboard/DashboardShell';
import { INDUSTRY_LABELS, OPPORTUNITY_STAGE_LABELS, ROLE_LABELS, TICKET_STATUS_LABELS } from '../../core/constants/app.constant';
import { listCustomers } from '../../core/services/customer.service';
import { listOpportunities } from '../../core/services/opportunity.service';
import { listTickets } from '../../core/services/serviceTicket.service';
import { listUsers } from '../../core/services/user.service';

const OPEN_TICKET_STATUSES = new Set(['open', 'assigned', 'in_progress']);
const CLOSED_OPPORTUNITY_STAGES = new Set(['won', 'lost']);
const CHART_COLORS = ['#5b3ee4', '#168c75', '#e58a27', '#3478c8', '#d14d72', '#6b7280', '#8b5cf6', '#0891b2', '#84a82b', '#c2410c', '#0f766e'];

const groupRecords = (records, getKey, labels = {}) => {
  const counts = records.reduce((result, record) => {
    const key = getKey(record) || 'unknown';
    result.set(key, (result.get(key) || 0) + 1);
    return result;
  }, new Map());

  return [...counts.entries()]
    .map(([key, count]) => ({
      label: labels[key] || key.replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()),
      count,
    }))
    .sort((left, right) => right.count - left.count);
};

const AdminDashboard = () => {
  const [records, setRecords] = useState(null);
  const [selectedGraph, setSelectedGraph] = useState('users');
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;

    Promise.all([listUsers(), listCustomers({}), listOpportunities({}), listTickets({})])
      .then(([users, customers, opportunities, tickets]) => {
        if (!cancelled) {
          setRecords({ users, customers, opportunities, tickets });
        }
      })
      .catch(() => {
        if (!cancelled) setError('Dashboard data could not be loaded. Please try again later.');
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const users = records?.users || [];
  const customers = records?.customers || [];
  const openOpportunities = (records?.opportunities || []).filter(
    (opportunity) => !CLOSED_OPPORTUNITY_STAGES.has(opportunity.stage),
  );
  const openTickets = (records?.tickets || []).filter(
    (ticket) => OPEN_TICKET_STATUSES.has(ticket.status),
  );

  const graphs = {
    users: {
      title: 'Users by role',
      description: 'Team members across this tenant',
      centerLabel: 'users',
      total: users.length,
      rows: groupRecords(users, (user) => user.role, ROLE_LABELS),
    },
    customers: {
      title: 'Customers by industry',
      description: 'Customer accounts across all teams',
      centerLabel: 'customers',
      total: customers.length,
      rows: groupRecords(customers, (customer) => customer.industry, INDUSTRY_LABELS),
    },
    opportunities: {
      title: 'Open opportunities by stage',
      description: 'Won and lost opportunities are excluded',
      centerLabel: 'opportunities',
      total: openOpportunities.length,
      rows: groupRecords(openOpportunities, (opportunity) => opportunity.stage, OPPORTUNITY_STAGE_LABELS),
    },
    tickets: {
      title: 'Open service tickets by status',
      description: 'Includes open, assigned, and in-progress tickets',
      centerLabel: 'tickets',
      total: openTickets.length,
      rows: groupRecords(openTickets, (ticket) => ticket.status, TICKET_STATUS_LABELS),
    },
  };

  const stats = [
    { id: 'users', label: 'Users', value: records ? users.length : '—', hint: 'Team members in this tenant' },
    { id: 'customers', label: 'Customers', value: records ? customers.length : '—', hint: 'All teams' },
    { id: 'opportunities', label: 'Open opportunities', value: records ? openOpportunities.length : '—', hint: 'Excludes won and lost' },
    { id: 'tickets', label: 'Open service tickets', value: records ? openTickets.length : '—', hint: 'Open, assigned, or in progress' },
  ].map((stat) => ({
    ...stat,
    onClick: () => setSelectedGraph(stat.id),
    isActive: selectedGraph === stat.id,
  }));

  const activeGraph = graphs[selectedGraph];
  const chartSlices = activeGraph.rows.map((row, index) => ({
    ...row,
    color: CHART_COLORS[index % CHART_COLORS.length],
    ratio: activeGraph.total ? (row.count / activeGraph.total) * 100 : 0,
    percentage: activeGraph.total ? Math.round((row.count / activeGraph.total) * 100) : 0,
  }));
  const chartGradient = chartSlices.reduce((gradient, slice, index) => {
    const stop = index === chartSlices.length - 1 ? 100 : gradient.stop + slice.ratio;
    return {
      stop,
      segments: [...gradient.segments, `${slice.color} ${gradient.stop}% ${stop}%`],
    };
  }, { stop: 0, segments: [] }).segments.join(', ');

  return (
    <DashboardShell
      title="Tenant Administrator Dashboard"
      subtitle="Full visibility across every team, module and tenant setting."
      stats={stats}
    >
      <section className="rounded-lg border border-slate-200 bg-white p-5 sm:p-6" aria-labelledby="tenant-chart-heading">
        <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 id="tenant-chart-heading" className="text-base font-bold text-slate-900">{activeGraph.title}</h2>
            <p className="mt-1 text-sm text-slate-500">{activeGraph.description}</p>
          </div>
          <p className="text-sm font-semibold text-slate-600">{records ? `${activeGraph.total} total` : 'Loading...'}</p>
        </div>

        {error ? (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>
        ) : !records ? (
          <p className="py-8 text-center text-sm text-slate-500">Loading tenant data...</p>
        ) : (
          <div className="flex flex-col items-center gap-8 py-2 md:flex-row md:justify-center md:gap-12">
            <div
              className="relative grid size-56 shrink-0 place-items-center rounded-full transition-[background] duration-500 sm:size-64"
              style={{ background: `conic-gradient(${chartGradient || '#e8edf5 0 100%'})` }}
              role="img"
              aria-label={`${activeGraph.title}; ${activeGraph.total} total`}
            >
              <div className="grid size-36 place-items-center rounded-full bg-white text-center shadow-inner sm:size-40">
                <div>
                  <strong className="block text-3xl font-bold text-slate-900">{activeGraph.total}</strong>
                  <span className="text-xs font-medium capitalize text-slate-500">{activeGraph.centerLabel}</span>
                </div>
              </div>
            </div>
            <div className="w-full max-w-sm space-y-3" role="list" aria-label={`${activeGraph.title} legend`}>
              {chartSlices.length > 0 ? chartSlices.map((slice) => (
                <div key={slice.label} className="flex items-center justify-between gap-4 border-b border-slate-100 pb-3 last:border-0 last:pb-0" role="listitem">
                  <span className="flex min-w-0 items-center gap-2.5 text-sm font-medium text-slate-700">
                    <span className="size-3 shrink-0 rounded-sm" style={{ backgroundColor: slice.color }} aria-hidden="true" />
                    <span className="truncate">{slice.label}</span>
                  </span>
                  <span className="shrink-0 text-right text-sm font-semibold text-slate-800">{slice.percentage}% <span className="font-normal text-slate-500">({slice.count})</span></span>
                </div>
              )) : (
                <p className="py-4 text-center text-sm text-slate-500">No records available for this graph yet.</p>
              )}
            </div>
          </div>
        )}
      </section>
    </DashboardShell>
  );
};

export default AdminDashboard;