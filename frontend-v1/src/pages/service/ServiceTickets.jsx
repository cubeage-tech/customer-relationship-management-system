import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';

const ServiceTickets = () => {
  const { can, scopeFor } = usePermissions();

  return (
    <section>
      <PageHeader
        title="Service Tickets"
        subtitle={`Customer support requests — ${SCOPE_LABELS[scopeFor(MODULES.SERVICE_TICKETS)]}`}
        actions={can(PERMISSIONS.TICKETS_CREATE) && <Button>Raise ticket</Button>}
      />

      <EmptyState
        title="No service tickets"
        message={
          can(PERMISSIONS.TICKETS_RESOLVE)
            ? 'Tickets assigned to you will appear here with their customer, priority and SLA.'
            : 'Service tickets you have access to will be listed here.'
        }
      />
    </section>
  );
};

export default ServiceTickets;
