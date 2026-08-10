import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';

const Customers = () => {
  const { can, scopeFor } = usePermissions();

  return (
    <section>
      <PageHeader
        title="Customers"
        subtitle={`Accounts and contacts — ${SCOPE_LABELS[scopeFor(MODULES.CUSTOMERS)]}`}
        actions={
          can(PERMISSIONS.CUSTOMERS_CREATE) && <Button>Add customer</Button>
        }
      />

      <EmptyState
        title="No customers yet"
        message={
          can(PERMISSIONS.CUSTOMERS_CREATE)
            ? 'Add your first customer account to start tracking contacts, opportunities and service history.'
            : 'Customer accounts you have access to will be listed here.'
        }
      />
    </section>
  );
};

export default Customers;
