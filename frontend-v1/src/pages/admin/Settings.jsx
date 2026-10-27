import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import RoutePath from '../../core/constants/routes.constant';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import NotificationSettings from '../../components/notification/NotificationSettings';
import IndustrySettings from '../../components/customer/IndustrySettings';
import CustomFieldSettings from '../../components/customfield/CustomFieldSettings';
import BrandingSettings from '../../components/branding/BrandingSettings';
import PipelineStageSettings from '../../components/opportunity/PipelineStageSettings';
import { SupportFormSettings, TicketAssignmentRules } from '../../components/service/ServiceSettings';
import LeadAssignmentRules from '../../components/lead/LeadAssignmentRules';
import CommissionRulesSettings from '../../components/salesforce/CommissionRulesSettings';
import ThresholdSettings from '../../components/settings/ThresholdSettings';
import EditionSettings from '../../components/settings/EditionSettings';
import IntegrationSettings from '../../components/settings/IntegrationSettings';
import AiDataSettings from '../../components/settings/AiDataSettings';

/** NAV-M11 (SRS 12.4): Settings / Admin → Users & Roles, Pipeline Config, SLA Config, Integrations (and the rest). */
const SECTIONS = [
  { id: 'users', label: 'Users & roles' },
  { id: 'company', label: 'Company & industry', manage: true },
  { id: 'pipeline', label: 'Pipeline', manage: true },
  { id: 'sla', label: 'Service & SLA', manage: true },
  { id: 'notifications', label: 'Notifications', manage: true },
  { id: 'integrations', label: 'Integrations', manage: true },
  { id: 'privacy', label: 'AI & data', manage: true },
];

const Group = ({ id, title, children }) => (
  <section id={id} aria-labelledby={`${id}-title`} className="scroll-mt-20 mb-8">
    <h2 id={`${id}-title`} className="text-xs font-semibold uppercase tracking-wide text-slate-400 mb-2">{title}</h2>
    {children}
  </section>
);

const Settings = () => {
  const { can, roleLabel } = usePermissions();
  const manage = can(PERMISSIONS.SETTINGS_MANAGE);
  const sections = SECTIONS.filter((s) => (s.id === 'users' ? can(PERMISSIONS.USERS_VIEW) : !s.manage || manage));

  return (
    <section>
      <PageHeader
        title="Settings"
        subtitle={manage ? 'Tenant wide configuration for your CRM.' : `Read only view of tenant settings for your role (${roleLabel}).`}
      />

      {sections.length > 1 && (
        <nav aria-label="Settings sections" className="sticky top-0 z-10 -mx-1 mb-6 flex flex-wrap gap-2 bg-background/95 px-1 py-2 backdrop-blur">
          {sections.map((s) => (
            <a key={s.id} href={`#${s.id}`} className="px-3 py-1.5 rounded-md text-sm border border-slate-200 bg-white text-slate-600 hover:border-primary/50">
              {s.label}
            </a>
          ))}
        </nav>
      )}

      {can(PERMISSIONS.USERS_VIEW) && (
        <Group id="users" title="Users & roles">
          <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
            <p className="text-sm text-slate-500">Manage who has access to the CRM, which role they hold and what each role may do.</p>
            <div className="flex flex-wrap gap-4 mt-3 text-sm">
              <Link to={RoutePath.ADMIN_USERS} className="text-primary hover:underline">Manage users</Link>
              <Link to={RoutePath.ADMIN_ROLES} className="text-primary hover:underline">Roles &amp; permissions</Link>
              {can(PERMISSIONS.TEAMS_MANAGE) && <Link to={RoutePath.ADMIN_TEAMS} className="text-primary hover:underline">Sales teams</Link>}
            </div>
          </div>
        </Group>
      )}

      {manage && (
        <>
          <Group id="company" title="Company & industry">
            <BrandingSettings />
            {/* BIZ-2 */}
            <EditionSettings />
            <IndustrySettings />
            {/* ARC-9 */}
            <CustomFieldSettings />
          </Group>

          <Group id="pipeline" title="Pipeline">
            <PipelineStageSettings />
            <LeadAssignmentRules />
            <CommissionRulesSettings />
          </Group>

          <Group id="sla" title="Service & SLA">
            {/* SLA hours per priority and the discount approval threshold */}
            <ThresholdSettings />
            <SupportFormSettings />
            <TicketAssignmentRules />
          </Group>

          <Group id="notifications" title="Notifications">
            {/* NOT-6: every notification event and channel (includes the FR-5.3 SMS alerts) */}
            <NotificationSettings />
          </Group>

          <Group id="integrations" title="Integrations">
            <IntegrationSettings />
          </Group>

          <Group id="privacy" title="AI & data">
            {/* SEC-5 / SEC-6 */}
            <AiDataSettings />
            {/* SEC-8 */}
            <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
              <h2 className="font-semibold text-slate-900">Data-protection requests</h2>
              <p className="text-sm text-slate-500 mt-1">When a customer asks to see or delete their personal data, log and carry out the request here.</p>
              <Link to={RoutePath.ADMIN_DATA_REQUESTS} className="inline-block mt-3 text-sm text-primary hover:underline">Open data requests</Link>
            </div>
          </Group>
        </>
      )}
    </section>
  );
};

export default Settings;
