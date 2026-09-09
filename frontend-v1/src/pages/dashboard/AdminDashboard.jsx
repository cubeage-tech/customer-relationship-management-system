import { useEffect, useState } from 'react';
import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';
import { getTenantAdminDashboard } from '../../core/services/dashboard.service';

const AdminDashboard = () => {
  const [stats, setStats] = useState([
    { label: 'Users', value: '—', hint: 'Active users in this tenant' },
    { label: 'Customers', value: '—', hint: 'All teams' },
    { label: 'Open opportunities', value: '—', hint: 'All teams' },
    { label: 'Open service tickets', value: '—', hint: 'All teams' },
  ]);

  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDashboard = async () => {
      try {
        const data = await getTenantAdminDashboard();

        setStats([
          {
            label: 'Users',
            value: data?.totalUsers ?? 0,
            hint: 'Active users in this tenant',
          },
          {
            label: 'Customers',
            value: data?.totalCustomers ?? 0,
            hint: 'All teams',
          },
          {
            label: 'Open opportunities',
            value: data?.openOpportunities ?? 0,
            hint: 'All teams',
          },
          {
            label: 'Open service tickets',
            value: data?.openServiceTickets ?? 0,
            hint: 'All teams',
          },
        ]);
      } catch (error) {
        console.error('Failed to fetch tenant admin dashboard:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchDashboard();
  }, []);

  return (
    <DashboardShell
      title="Tenant Administrator Dashboard"
      subtitle="Full visibility across every team, module and tenant setting."
      stats={stats}
    >
      <EmptyState
        title="No tenant activity yet"
        message="Once users start working leads, quotations and service tickets, the tenant wide activity feed will appear here."
      />
    </DashboardShell>
  );
};

export default AdminDashboard;