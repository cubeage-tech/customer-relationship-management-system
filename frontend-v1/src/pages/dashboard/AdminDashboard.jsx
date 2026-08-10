import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';

const STATS = [
  { label: 'Users', value: '—', hint: 'Active users in this tenant' },
  { label: 'Customers', value: '—', hint: 'All teams' },
  { label: 'Open opportunities', value: '—', hint: 'All teams' },
  { label: 'Open service tickets', value: '—', hint: 'All teams' },
];

const AdminDashboard = () => {
  return (
    <DashboardShell
      title="Tenant Administrator Dashboard"
      subtitle="Full visibility across every team, module and tenant setting."
      stats={STATS}
    >
      <EmptyState
        title="No tenant activity yet"
        message="Once users start working leads, quotations and service tickets, the tenant wide activity feed will appear here."
      />
    </DashboardShell>
  );
};

export default AdminDashboard;
