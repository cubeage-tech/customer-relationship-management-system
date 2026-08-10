import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';

/** Report categories, each visible only to the roles that own them. */
const REPORTS = [
  {
    key: 'sales',
    title: 'Sales reports',
    description: 'Pipeline, conversion and win rate by team member.',
    permission: PERMISSIONS.REPORTS_SALES,
  },
  {
    key: 'campaign',
    title: 'Campaign reports',
    description: 'Campaign reach, leads generated and cost per lead.',
    permission: PERMISSIONS.REPORTS_CAMPAIGN,
  },
  {
    key: 'service',
    title: 'Service reports',
    description: 'Ticket volume, first response time and resolution time.',
    permission: PERMISSIONS.REPORTS_SERVICE,
  },
  {
    key: 'revenue',
    title: 'Revenue reports',
    description: 'Booked revenue, approved discounts and margin.',
    permission: PERMISSIONS.REPORTS_REVENUE,
  },
];

const Reports = () => {
  const { can, scopeFor } = usePermissions();

  const visibleReports = REPORTS.filter((report) => can(report.permission));

  return (
    <section>
      <PageHeader
        title="Reports"
        subtitle={`Reporting available to your role — ${SCOPE_LABELS[scopeFor(MODULES.REPORTS)]}`}
      />

      {visibleReports.length === 0 ? (
        <EmptyState
          title="No reports available"
          message="Your role has access to dashboards only. Ask your tenant administrator if you need reporting access."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {visibleReports.map((report) => (
            <article
              key={report.key}
              className="bg-white rounded-lg border border-slate-200 p-5"
            >
              <h2 className="font-semibold text-slate-900">{report.title}</h2>
              <p className="text-sm text-slate-500 mt-1">{report.description}</p>
            </article>
          ))}
        </div>
      )}
    </section>
  );
};

export default Reports;
