import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import RoutePath from '../../core/constants/routes.constant';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import SmsAlertSettings from '../../components/communication/SmsAlertSettings';
import IndustrySettings from '../../components/customer/IndustrySettings';
import BrandingSettings from '../../components/branding/BrandingSettings';
import PipelineStageSettings from '../../components/opportunity/PipelineStageSettings';
import { SupportFormSettings, TicketAssignmentRules } from '../../components/service/ServiceSettings';
import LeadAssignmentRules from '../../components/lead/LeadAssignmentRules';
import CommissionRulesSettings from '../../components/salesforce/CommissionRulesSettings';

const Settings = () => {
  const { can, roleLabel } = usePermissions();

  return (
    <section>
      <PageHeader
        title="Settings"
        subtitle={
          can(PERMISSIONS.SETTINGS_MANAGE)
            ? 'Tenant wide configuration for your CRM.'
            : `Read only view of tenant settings for your role (${roleLabel}).`
        }
      />

      {can(PERMISSIONS.USERS_VIEW) && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
          <h2 className="font-semibold text-slate-900">Users &amp; roles</h2>
          <p className="text-sm text-slate-500 mt-1">
            Manage who has access to the CRM and which role they hold.
          </p>
          <div className="flex gap-4 mt-3 text-sm">
            <Link to={RoutePath.ADMIN_USERS} className="text-blue-600 hover:underline">
              Manage users
            </Link>
            <Link to={RoutePath.ADMIN_ROLES} className="text-blue-600 hover:underline">
              View role matrix
            </Link>
          </div>
        </div>
      )}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <BrandingSettings />}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <IndustrySettings />}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <PipelineStageSettings />}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <LeadAssignmentRules />}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <CommissionRulesSettings />}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <SupportFormSettings />}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <TicketAssignmentRules />}

      {can(PERMISSIONS.SETTINGS_MANAGE) && <SmsAlertSettings />}

      <EmptyState
        title="No other settings configured"
        message="Tenant preferences such as pipeline stages, ticket categories and notification rules will appear here."
      />
    </section>
  );
};

export default Settings;
