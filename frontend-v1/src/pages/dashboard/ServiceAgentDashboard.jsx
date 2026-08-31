import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';

const STATS = [
  { label: 'Assigned tickets', value: '3', hint: 'Open and assigned to you' },
  { label: 'Due today', value: '—', hint: 'Breaching SLA soon' },
  { label: 'Resolved this week', value: '—', hint: 'By you' },
  { label: 'Average resolution', value: '—', hint: 'Time to close' },
];

const ServiceAgentDashboard = () => (
  <DashboardShell
    title="Service Dashboard"
    subtitle="Service tickets assigned to you and their resolution status."
    stats={STATS}
  >
    <EmptyState
      title="No tickets assigned"
      message="Tickets assigned to you will appear here with their customer and SLA details."
    />
  </DashboardShell>
);

export default ServiceAgentDashboard;