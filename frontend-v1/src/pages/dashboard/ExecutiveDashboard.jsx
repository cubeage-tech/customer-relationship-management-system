import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';

const STATS = [
  { label: 'Total pipeline', value: '—', hint: 'Organisation wide' },
  { label: 'Revenue this quarter', value: '—', hint: 'Won deals' },
  { label: 'New customers', value: '—', hint: 'This quarter' },
  { label: 'Service backlog', value: '—', hint: 'Open tickets' },
];

const ExecutiveDashboard = () => {
  return (
    <DashboardShell
      title="Executive Dashboard"
      subtitle="Organisation wide view of sales, marketing and service performance."
      stats={STATS}
    >
      <EmptyState
        title="No reporting data yet"
        message="Once your teams start closing deals and resolving tickets, organisation wide trends will appear here."
      />
    </DashboardShell>
  );
};

export default ExecutiveDashboard;
