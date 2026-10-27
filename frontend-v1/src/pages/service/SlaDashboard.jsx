import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import ServiceNav from '../../components/service/ServiceNav';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getTicketSummary, listTickets } from '../../core/services/serviceTicket.service';
import { getServiceReport } from '../../core/services/analytics.service';
import { periodRange } from '../../core/utils/reportPeriod';

const OPEN = new Set(['open', 'assigned', 'in_progress']);
const SLA = {
  breached: { label: 'Breached', className: 'bg-red-50 text-red-700' },
  at_risk: { label: 'At risk', className: 'bg-amber-50 text-amber-700' },
  on_track: { label: 'On track', className: 'bg-emerald-50 text-emerald-700' },
};
const pct = (v) => (v == null ? '—' : `${v}%`);

const dueIn = (iso) => {
  const minutes = Math.round((new Date(iso).getTime() - Date.now()) / 60000);
  const abs = Math.abs(minutes);
  const text = abs < 60 ? `${abs} min` : abs < 60 * 48 ? `${Math.round(abs / 60)} h` : `${Math.round(abs / 1440)} d`;
  return minutes < 0 ? `${text} overdue` : `in ${text}`;
};

/**
 * NAV-M07: the SLA dashboard — open tickets by SLA state, those breached or at risk first (with how
 * long until / since their deadline), and this month's SLA compliance by priority and technician.
 */
const SlaDashboard = () => {
  const { can } = usePermissions();
  const [summary, setSummary] = useState(null);
  const [tickets, setTickets] = useState(null);
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getTicketSummary().then(setSummary).catch((err) => setError(apiErrorMessage(err, 'Could not load the SLA figures.')));
    listTickets({}).then((rows) => setTickets((rows ?? []).filter((t) => OPEN.has(t.status))
      .sort((a, b) => new Date(a.slaDueAt) - new Date(b.slaDueAt)))).catch(() => setTickets([]));
    if (can(PERMISSIONS.REPORTS_SERVICE)) {
      getServiceReport(periodRange('month')).then(setReport).catch(() => setReport(null));
    }
  }, [can]);

  const urgent = (tickets ?? []).filter((t) => t.slaStatus === 'breached' || t.slaStatus === 'at_risk');

  return (
    <section>
      <PageHeader title="SLA dashboard" subtitle="Open service tickets against their response deadlines." />
      <ServiceNav />
      {error && <p className="text-sm text-red-600 mb-4">{error}</p>}

      {summary && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 mb-6">
          <StatCard label="Breached" value={summary.breached} hint="past their SLA deadline" />
          <StatCard label="At risk" value={summary.atRisk} hint="close to the deadline" />
          <StatCard label="On track" value={summary.onTrack} hint={`${summary.dueWithin24h} due within 24 h`} />
          <StatCard label="Resolved this week" value={summary.resolvedThisWeek}
            hint={summary.averageResolutionHours == null ? '' : `avg ${summary.averageResolutionHours} h to resolve`} />
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <h2 className="font-semibold text-slate-900">Needs attention now</h2>
        {tickets === null && <p className="text-sm text-slate-400 mt-2">Loading…</p>}
        {tickets !== null && urgent.length === 0 && <p className="text-sm text-slate-400 mt-2">No ticket is breached or at risk.</p>}
        {urgent.length > 0 && (
          <ul className="mt-3 divide-y divide-slate-100">
            {urgent.map((t) => (
              <li key={t.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5 text-sm">
                <Link to={`/service-tickets/${t.id}`} className="min-w-0 hover:underline">
                  <span className="font-medium text-slate-900">#{t.id} {t.subject}</span>
                  <span className="text-slate-400"> · {t.customerName} · {t.priority} · {t.technicianName || 'unassigned'}</span>
                </Link>
                <span className="flex items-center gap-2">
                  <span className="text-xs text-slate-500">{dueIn(t.slaDueAt)}</span>
                  <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${SLA[t.slaStatus]?.className || ''}`}>{SLA[t.slaStatus]?.label}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {report && (
        <div className="grid gap-6 lg:grid-cols-2">
          {[['By priority', report.byPriority], ['By technician', report.byTechnician]].map(([title, rows]) => (
            <div key={title} className="bg-white border border-slate-200 rounded-lg p-5">
              <h2 className="font-semibold text-slate-900">{title} — this month</h2>
              <table className="min-w-full text-sm mt-3">
                <thead><tr className="text-left text-slate-500 border-b border-slate-200">
                  <th className="py-2 pr-3 font-medium" /><th className="py-2 px-3 font-medium">Raised</th>
                  <th className="py-2 px-3 font-medium">Resolved</th><th className="py-2 px-3 font-medium">SLA met</th>
                </tr></thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.key ?? 'none'} className="border-b border-slate-100">
                      <td className="py-2 pr-3 text-slate-800">{r.label}</td><td className="py-2 px-3">{r.created}</td>
                      <td className="py-2 px-3">{r.resolved}</td><td className="py-2 px-3">{pct(r.slaCompliancePercent)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
        </div>
      )}
    </section>
  );
};

export default SlaDashboard;
