import SectionNav from '../common/SectionNav';
import RoutePath from '../../core/constants/routes.constant';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';

/** NAV-M07 (SRS 12.4): Service Tickets → List, SLA dashboard (and the service report). */
const ServiceNav = () => {
  const { can } = usePermissions();
  const items = [
    { to: RoutePath.SERVICE_TICKETS, label: 'Tickets' },
    can(PERMISSIONS.TICKETS_VIEW) && { to: RoutePath.SLA_DASHBOARD, label: 'SLA dashboard' },
    can(PERMISSIONS.REPORTS_SERVICE) && { to: '/reports/service', label: 'Service report' },
  ].filter(Boolean);
  return <SectionNav items={items} label="Service sections" />;
};

export default ServiceNav;
