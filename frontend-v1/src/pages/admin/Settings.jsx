import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import RoutePath from '../../core/constants/routes.constant';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';

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

      <EmptyState
        title="No other settings configured"
        message="Tenant preferences such as pipeline stages, ticket categories and notification rules will appear here."
      />
    </section>
  );
};

export default Settings;
