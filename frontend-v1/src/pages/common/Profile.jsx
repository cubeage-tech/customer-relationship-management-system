import PageHeader from '../../components/common/PageHeader';
import { useAuth } from '../../core/hooks/useAuth';
import { usePermissions } from '../../core/hooks/usePermissions';
import { ROLE_DESCRIPTIONS } from '../../core/constants/app.constant';

const Profile = () => {
  const { user } = useAuth();
  const { role, roleLabel, permissions } = usePermissions();

  return (
    <section>
      <PageHeader title="My Profile" subtitle="Your CRM account and access level." />

      <div className="bg-white border border-slate-200 rounded-lg p-5 max-w-xl">
        <dl className="grid grid-cols-3 gap-y-3 text-sm">
          <dt className="text-slate-500">Name</dt>
          <dd className="col-span-2 text-slate-900">{user?.name || '—'}</dd>

          <dt className="text-slate-500">Email</dt>
          <dd className="col-span-2 text-slate-900">{user?.email || '—'}</dd>

          <dt className="text-slate-500">Role</dt>
          <dd className="col-span-2 text-slate-900">
            {roleLabel || '—'}
            {role && <code className="ml-2 text-xs text-slate-500">{role}</code>}
          </dd>

          <dt className="text-slate-500">Access</dt>
          <dd className="col-span-2 text-slate-600">
            {role ? ROLE_DESCRIPTIONS[role] : '—'}
            <span className="block text-xs text-slate-400 mt-1">
              {permissions.length} permissions granted
            </span>
          </dd>
        </dl>
      </div>
    </section>
  );
};

export default Profile;
