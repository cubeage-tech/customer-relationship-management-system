import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';

const STATS = [
  { label: 'My leads', value: '—', hint: 'Leads you own' },
  { label: 'My opportunities', value: '—', hint: 'Open deals' },
  { label: 'My quotations', value: '—', hint: 'Draft and sent' },
  { label: 'Follow-ups due', value: '—', hint: 'Activities due today' },
];

const SalesExecutiveDashboard = () => {
  return (
    <DashboardShell
      title="Sales Executive Dashboard"
      subtitle="Your leads, opportunities, quotations and follow-ups."
      stats={STATS}
    >
      <EmptyState
        title="No follow-ups scheduled"
        message="Create a lead and schedule an activity to see your follow-up list here."
      />
    </DashboardShell>
  );
};

export default SalesExecutiveDashboard;
