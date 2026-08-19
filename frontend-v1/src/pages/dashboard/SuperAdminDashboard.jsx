import { useEffect, useState } from 'react';
import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';
import { listTenants } from '../../core/services/tenant.service';

const SuperAdminDashboard = () => {
  const [tenantCount, setTenantCount] = useState('—');

  useEffect(() => {
    let cancelled = false;

    listTenants()
      .then((tenants) => {
        if (!cancelled) setTenantCount(tenants?.length ?? '—');
      })
      .catch(() => {
        if (!cancelled) setTenantCount('—');
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // Sales/product figures stay placeholders — those modules aren't built yet, so this
  // deliberately doesn't fabricate numbers the backend can't yet provide.
  const stats = [
    { label: 'Tenants', value: tenantCount, hint: 'Companies using SmartCRM AI' },
    { label: 'Total sales (all tenants)', value: '—', hint: 'Coming once the sales module ships' },
    { label: 'Products sold', value: '—', hint: 'Coming once the sales module ships' },
    { label: 'Active subscriptions', value: '—', hint: 'By plan' },
  ];

  return (
    <DashboardShell
      title="Platform Dashboard"
      subtitle="Cross-tenant visibility for Devniks/Cubeage platform staff."
      stats={stats}
    >
      <EmptyState
        title="No cross-tenant activity yet"
        message="Sales and product analytics across tenants will appear here once the sales module is built."
      />
    </DashboardShell>
  );
};

export default SuperAdminDashboard;
