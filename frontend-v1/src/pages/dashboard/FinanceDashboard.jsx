import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';

const STATS = [
  { label: 'Discounts to approve', value: '—', hint: 'Waiting on you' },
  { label: 'Approved this month', value: '—', hint: 'Quotation discounts' },
  { label: 'Revenue booked', value: '—', hint: 'Won deals this month' },
  { label: 'Average discount', value: '—', hint: 'Across approved quotations' },
];

const FinanceDashboard = () => {
  return (
    <DashboardShell
      title="Finance Dashboard"
      subtitle="Discount approvals and revenue reporting."
      stats={STATS}
    >
      <EmptyState
        title="No approvals pending"
        message="Quotations that need a discount approval will be queued here."
      />
    </DashboardShell>
  );
};

export default FinanceDashboard;
