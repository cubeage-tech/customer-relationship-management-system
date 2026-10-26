import { NavLink, useLocation } from 'react-router-dom';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';

/** NAV-M01 (SRS 12.4): Dashboard → Overview and the Sales, Customer, Revenue and Team report screens. */
const SECTIONS = [
  { tab: 'sales', label: 'Sales', permissions: [PERMISSIONS.REPORTS_SALES] },
  { tab: 'customers', label: 'Customers', permissions: [PERMISSIONS.REPORTS_SALES, PERMISSIONS.REPORTS_CAMPAIGN] },
  { tab: 'revenue', label: 'Revenue', permissions: [PERMISSIONS.REPORTS_REVENUE] },
  { tab: 'team', label: 'Team', permissions: [PERMISSIONS.REPORTS_SALES] },
];

const link = 'px-3 py-1.5 rounded-md text-sm border';
const on = 'border-primary/50 bg-primary/5 text-primary';
const off = 'border-slate-200 bg-white text-slate-600 hover:border-primary/50';

const DashboardNav = () => {
  const { can } = usePermissions();
  const { pathname } = useLocation();
  const sections = SECTIONS.filter((s) => s.permissions.some((p) => can(p)));
  if (sections.length === 0) return null;

  return (
    <nav aria-label="Dashboard sections" className="flex flex-wrap gap-2 mb-4">
      <NavLink to={pathname} end className={`${link} ${on}`} aria-current="page">Overview</NavLink>
      {sections.map((s) => (
        <NavLink key={s.tab} to={`/reports/${s.tab}`} className={`${link} ${off}`}>{s.label} report</NavLink>
      ))}
    </nav>
  );
};

export default DashboardNav;
