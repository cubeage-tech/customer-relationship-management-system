import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';

const Approvals = () => {
  const { can } = usePermissions();

  const subtitle = can(PERMISSIONS.QUOTATIONS_APPROVE_DISCOUNT)
    ? 'Quotation discounts waiting for finance approval.'
    : 'Quotations waiting for your approval.';

  return (
    <section>
      <PageHeader title="Approvals" subtitle={subtitle} />

      <EmptyState
        title="Nothing to approve"
        message="Quotations submitted for approval will be queued here."
      />
    </section>
  );
};

export default Approvals;
