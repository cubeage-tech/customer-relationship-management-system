import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';

const Leads = () => {
  const { can, scopeFor } = usePermissions();

  return (
    <section>
      <PageHeader
        title="Leads"
        subtitle={`Inbound and outbound leads — ${SCOPE_LABELS[scopeFor(MODULES.LEADS)]}`}
        actions={can(PERMISSIONS.LEADS_CREATE) && <Button>Add lead</Button>}
      />

      <EmptyState
        title="No leads yet"
        message={
          can(PERMISSIONS.LEADS_CREATE)
            ? 'Capture a lead to start qualifying it into an opportunity.'
            : 'Leads you have access to will be listed here.'
        }
      />
    </section>
  );
};

export default Leads;
