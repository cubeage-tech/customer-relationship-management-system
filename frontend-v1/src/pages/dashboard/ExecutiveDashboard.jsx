import DashboardShell from '../../components/dashboard/DashboardShell';
import HeadlineMetrics from '../../components/analytics/HeadlineMetrics';
import SalesPerformance from '../../components/opportunity/SalesPerformance';

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
      <HeadlineMetrics />
      <SalesPerformance />
    </DashboardShell>
  );
};

export default ExecutiveDashboard;
