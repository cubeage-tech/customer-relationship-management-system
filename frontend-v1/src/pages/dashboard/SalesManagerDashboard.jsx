import DashboardShell from '../../components/dashboard/DashboardShell';
import SalesPerformance from '../../components/opportunity/SalesPerformance';

const STATS = [
  { label: 'Team leads', value: '—', hint: 'Open leads across the team' },
  { label: 'Team pipeline', value: '—', hint: 'Value of open opportunities' },
  { label: 'Quotations to approve', value: '—', hint: 'Waiting on you' },
  { label: 'Closed this month', value: '—', hint: 'Won deals' },
];

const SalesManagerDashboard = () => {
  return (
    <DashboardShell
      title="Sales Manager Dashboard"
      subtitle="Team pipeline, quotation approvals and team performance."
      stats={STATS}
    >
      <SalesPerformance />
    </DashboardShell>
  );
};

export default SalesManagerDashboard;
