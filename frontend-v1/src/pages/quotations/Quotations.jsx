import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';

const Quotations = () => {
  const { can, scopeFor } = usePermissions();

  return (
    <section>
      <PageHeader
        title="Quotations"
        subtitle={`Quotes raised against opportunities — ${SCOPE_LABELS[scopeFor(MODULES.QUOTATIONS)]}`}
        actions={
          can(PERMISSIONS.QUOTATIONS_CREATE) && <Button>Create quotation</Button>
        }
      />

      <EmptyState
        title="No quotations yet"
        message={
          can(PERMISSIONS.QUOTATIONS_CREATE)
            ? 'Create a quotation from an opportunity to send pricing to the customer.'
            : 'Quotations you have access to will be listed here for review.'
        }
      />
    </section>
  );
};

export default Quotations;
