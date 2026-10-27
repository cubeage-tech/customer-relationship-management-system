import SectionNav from '../common/SectionNav';
import RoutePath from '../../core/constants/routes.constant';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';

/**
 * NAV-M08 (SRS 12.4): Campaigns → List (with "New campaign"), Segmentation, Promotions and the
 * Performance report.
 */
const CampaignsNav = () => {
  const { can } = usePermissions();
  const items = [
    { to: RoutePath.CAMPAIGNS, label: 'Campaigns' },
    { to: RoutePath.SEGMENTS, label: 'Segments' },
    { to: RoutePath.PROMOTIONS, label: 'Promotions' },
    can(PERMISSIONS.REPORTS_CAMPAIGN) && { to: '/reports/campaigns', label: 'Performance report' },
  ].filter(Boolean);
  return <SectionNav items={items} label="Campaign sections" />;
};

export default CampaignsNav;
