import { useSearchParams } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import { USER_ROLES } from '../../core/constants/app.constant';
import { usePermissions } from '../../core/hooks/usePermissions';
import AttendancePanel from '../../components/salesforce/AttendancePanel';
import TargetsPanel from '../../components/salesforce/TargetsPanel';
import ActivitiesPanel from '../../components/salesforce/ActivitiesPanel';
import CommissionsPanel from '../../components/salesforce/CommissionsPanel';

const { ADMIN, SALES_MANAGER, SALES_EXECUTIVE, EXECUTIVE_OWNER, FINANCE_APPROVER } = USER_ROLES;

/**
 * Module 8 — sales force management, one page with a tab per area. Who sees which tab mirrors the
 * API: everyone checks in; sales roles see targets and activity; managers see their team.
 * NAV-M09 (SRS 12.4): Sales Team → Targets, Attendance, Commission, Activity Log; the tab is in the
 * address (?tab=commissions) so each screen can be linked to.
 */
const SalesForce = () => {
  const { is } = usePermissions();
  const tabs = [
    { key: 'targets', label: 'Targets', show: is([ADMIN, SALES_MANAGER, SALES_EXECUTIVE, EXECUTIVE_OWNER]) },
    { key: 'attendance', label: 'Attendance', show: true },
    { key: 'commissions', label: 'Commission', show: is([ADMIN, SALES_MANAGER, SALES_EXECUTIVE, EXECUTIVE_OWNER, FINANCE_APPROVER]) },
    { key: 'activities', label: 'Activity log', show: is([ADMIN, SALES_MANAGER, SALES_EXECUTIVE, EXECUTIVE_OWNER]) },
  ].filter((t) => t.show);
  const [params, setParams] = useSearchParams();
  const tab = tabs.some((t) => t.key === params.get('tab')) ? params.get('tab') : tabs[0].key;
  const setTab = (key) => setParams({ tab: key }, { replace: true });

  return (
    <section>
      <PageHeader title="Sales Team" subtitle="Targets, attendance, commission and the activity log." />
      <div className="mb-4 flex flex-wrap gap-2" role="tablist">
        {tabs.map((t) => (
          <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}
            className={`rounded-full border px-3 py-1.5 text-sm font-medium ${tab === t.key ? 'border-primary bg-primary text-white' : 'border-slate-300 bg-white text-slate-700 hover:border-primary/50'}`}>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'attendance' && <AttendancePanel canSeeTeam={is([ADMIN, SALES_MANAGER, EXECUTIVE_OWNER])} />}
      {tab === 'targets' && <TargetsPanel canManage={is([ADMIN, SALES_MANAGER])} />}
      {tab === 'activities' && <ActivitiesPanel canLog={is([ADMIN, SALES_MANAGER, SALES_EXECUTIVE])} />}
      {tab === 'commissions' && <CommissionsPanel canSettle={is([ADMIN, FINANCE_APPROVER])} />}
    </section>
  );
};

export default SalesForce;
