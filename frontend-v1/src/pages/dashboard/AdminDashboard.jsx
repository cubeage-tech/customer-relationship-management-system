import { useEffect, useState } from 'react';
import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';
import { getTenantAdminDashboard } from '../../core/services/dashboard.service';
import UpgradePopup from '../../components/common/UpgradePopup';
import ApiService from '../../core/services/api.service';

// Statuses where the owner should be prompted to renew (expired tenants are read-only).
const RENEW_STATUSES = ['past_due', 'expired'];

const AdminDashboard = () => {
  const [stats, setStats] = useState([
    { label: 'Users', value: '—', hint: 'Active users in this tenant' },
    { label: 'Customers', value: '—', hint: 'All teams' },
    { label: 'Open opportunities', value: '—', hint: 'All teams' },
    { label: 'Open service tickets', value: '—', hint: 'All teams' },
  ]);

  const [loading, setLoading] = useState(true);

  const [subscription, setSubscription] = useState(null);
  const [showUpgradePopup, setShowUpgradePopup] = useState(false);

  const loadSubscription = () =>
    ApiService.getCurrentSubscription()
      .then((current) => {
        setSubscription(current);
        setShowUpgradePopup(RENEW_STATUSES.includes(current?.status));
      })
      .catch((error) => console.error('Failed to fetch subscription:', error));

  useEffect(() => {
    loadSubscription();
  }, []);

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
      <UpgradePopup
        isOpen={showUpgradePopup}
        onClose={() => setShowUpgradePopup(false)}
        currentPlan={subscription?.plan}
        onPaymentSuccess={loadSubscription}
      />
    </DashboardShell>
  );
};

export default AdminDashboard;