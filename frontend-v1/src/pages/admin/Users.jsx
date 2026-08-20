import { useEffect, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { ROLE_LABELS, ROLE_OPTIONS, TEAM_ROLES } from '../../core/constants/app.constant';
import { listUsers, createUser } from '../../core/services/user.service';

const TEAM_ROLE_OPTIONS = ROLE_OPTIONS.filter((option) => TEAM_ROLES.includes(option.value));

const INITIAL_FORM = { fullName: '', email: '', password: '', role: TEAM_ROLES[0] };

const Users = () => {
  const { can } = usePermissions();
  const canManage = can(PERMISSIONS.USERS_MANAGE);

  const [roleFilter, setRoleFilter] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [error, setError] = useState('');

  const refresh = () => {
    listUsers()
      .then((data) => setUsers(data ?? []))
      .catch(() => setUsers([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
  }, []);

  const visibleUsers = roleFilter ? users.filter((u) => u.role === roleFilter) : users;

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddUser = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createUser(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
    } catch {
      setError('Could not add this user. Check the details and try again.');
    }
  };

  return (
    <section>
      <PageHeader
        title="Users"
        subtitle="People in this tenant and the CRM role assigned to each of them."
        actions={
          canManage && (
            <Button onClick={() => setShowForm((prev) => !prev)}>
              {showForm ? 'Cancel' : 'Add user'}
            </Button>
          )
        }
      />

      {canManage && showForm && (
        <form
          onSubmit={handleAddUser}
          className="mb-6 grid gap-3 sm:grid-cols-2 border border-slate-300 rounded p-4"
        >
          <input
            type="text"
            name="fullName"
            placeholder="Full name"
            value={form.fullName}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
            required
          />
          <input
            type="email"
            name="email"
            placeholder="Work email"
            value={form.email}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
            required
          />
          <input
            type="password"
            name="password"
            placeholder="Temporary password"
            value={form.password}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
            required
          />
          <select
            name="role"
            value={form.role}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          >
            {TEAM_ROLE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>

          {error && <p className="form-error sm:col-span-2">{error}</p>}
          <div className="sm:col-span-2">
            <Button type="submit">Add user</Button>
          </div>
        </form>
      )}

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

      {loading ? null : visibleUsers.length === 0 ? (
        <EmptyState
          title="No users to show"
          message={
            canManage
              ? 'Add a team member and assign them a CRM role to get started.'
              : 'Users in your team will be listed here.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Name</th>
                <th className="py-2 pr-4">Email</th>
                <th className="py-2 pr-4">Role</th>
                <th className="py-2 pr-4">Status</th>
              </tr>
            </thead>
            <tbody>
              {visibleUsers.map((u) => (
                <tr key={u.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">{u.fullName}</td>
                  <td className="py-2 pr-4">{u.email}</td>
                  <td className="py-2 pr-4">{ROLE_LABELS[u.role] || u.role}</td>
                  <td className="py-2 pr-4">{u.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default Users;
