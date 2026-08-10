import { useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { ROLE_OPTIONS } from '../../core/constants/app.constant';

const Users = () => {
  const { can } = usePermissions();
  const [roleFilter, setRoleFilter] = useState('');

  const canManage = can(PERMISSIONS.USERS_MANAGE);

  return (
    <section>
      <PageHeader
        title="Users"
        subtitle="People in this tenant and the CRM role assigned to each of them."
        actions={canManage && <Button>Invite user</Button>}
      />

      <div className="mb-4">
        <label className="text-sm text-slate-600">
          Filter by role
          <select
            name="role"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="ml-2 border border-slate-300 rounded px-2 py-1"
          >
            <option value="">All roles</option>
            {ROLE_OPTIONS.map((option) => (
              // value is the internal role name, label is the display name
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <EmptyState
        title="No users to show"
        message={
          canManage
            ? 'Invite a team member and assign them a CRM role to get started.'
            : 'Users in your team will be listed here.'
        }
      />
    </section>
  );
};

export default Users;
