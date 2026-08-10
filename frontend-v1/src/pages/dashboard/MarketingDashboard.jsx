import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';

const STATS = [
  { label: 'Active campaigns', value: '—', hint: 'Currently running' },
  { label: 'Leads generated', value: '—', hint: 'From campaigns' },
  { label: 'Qualified leads', value: '—', hint: 'Passed to sales' },
  { label: 'Conversion rate', value: '—', hint: 'Lead to opportunity' },
];

const MarketingDashboard = () => {
  return (
    <DashboardShell
      title="Marketing Dashboard"
      subtitle="Campaign performance and the leads they generate."
      stats={STATS}
    >
      <EmptyState
        title="No campaigns yet"
        message="Create a campaign to start tracking generated leads and conversion here."
      />
    </DashboardShell>
  );
};

export default MarketingDashboard;
