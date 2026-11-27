import DashboardShell from '../../components/dashboard/DashboardShell';
import HeadlineMetrics from '../../components/analytics/HeadlineMetrics';
import TasksPanel from '../../components/task/TasksPanel';

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
      <HeadlineMetrics />
      {/* FR-AI-7.3 */}
      <TasksPanel />
    </DashboardShell>
  );
};

export default SalesExecutiveDashboard;
