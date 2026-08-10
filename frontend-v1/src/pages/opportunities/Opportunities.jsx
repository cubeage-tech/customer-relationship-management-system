import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';

const Opportunities = () => {
  const { can, scopeFor } = usePermissions();

  return (
    <section>
      <PageHeader
        title="Opportunities"
        subtitle={`Deals in the sales pipeline — ${SCOPE_LABELS[scopeFor(MODULES.OPPORTUNITIES)]}`}
        actions={
          can(PERMISSIONS.OPPORTUNITIES_CREATE) && <Button>Add opportunity</Button>
        }
      />

      <EmptyState
        title="No opportunities yet"
        message={
          can(PERMISSIONS.OPPORTUNITIES_CREATE)
            ? 'Convert a qualified lead into an opportunity to start tracking deal value and stage.'
            : 'Opportunities you have access to will be listed here.'
        }
      />
    </section>
  );
};

export default Opportunities;
