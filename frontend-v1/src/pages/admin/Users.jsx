import { useEffect, useState } from 'react';
import { CheckCircle2, CircleAlert, Mail, Plus, Search, ShieldCheck, UserRound, X } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
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
  const [search, setSearch] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [toast, setToast] = useState(null);

  const refresh = () => {
    listUsers()
      .then((data) => setUsers(data ?? []))
      .catch(() => setUsers([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const visibleUsers = users.filter((user) => {
    const matchesRole = !roleFilter || user.role === roleFilter;
    const normalizedSearch = search.trim().toLowerCase();
    const matchesSearch = !normalizedSearch || `${user.fullName} ${user.email}`.toLowerCase().includes(normalizedSearch);
    return matchesRole && matchesSearch;
  });

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddUser = async (e) => {
    e.preventDefault();
    try {
      await createUser(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
      setToast({ type: 'success', message: `${form.fullName} was added to your team.` });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not add this user. Check the details and try again.',
      });
    }
  };

  return (
    <section className="space-y-5">
      {toast && (
        <div className={`fixed right-5 top-20 z-50 flex w-[min(26rem,calc(100vw-2.5rem))] items-start gap-3 rounded-lg border bg-white p-4 shadow-xl ${toast.type === 'success' ? 'border-emerald-200' : 'border-rose-200'}`} role={toast.type === 'error' ? 'alert' : 'status'} aria-live={toast.type === 'error' ? 'assertive' : 'polite'}>
          <span className={`mt-0.5 grid size-8 shrink-0 place-items-center rounded-full ${toast.type === 'success' ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}`}>
            {toast.type === 'success' ? <CheckCircle2 size={18} aria-hidden="true" /> : <CircleAlert size={18} aria-hidden="true" />}
          </span>
          <p className="flex-1 pt-1 text-sm font-medium text-slate-800">{toast.message}</p>
          <button type="button" className="grid size-8 shrink-0 place-items-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" onClick={() => setToast(null)} aria-label="Dismiss message"><X size={16} aria-hidden="true" /></button>
        </div>
      )}

      <PageHeader
        title="Users"
        subtitle="People in this tenant and the CRM role assigned to each of them."
        actions={
          canManage && (
            <Button onClick={() => setShowForm((prev) => !prev)} icon={showForm ? X : Plus}>
              {showForm ? 'Close form' : 'Add user'}
            </Button>
          )
        }
      />

      {canManage && showForm && (
        <div className="fixed inset-0 z-40 flex items-center justify-center overflow-y-auto p-4 sm:p-6" onKeyDown={(event) => {
          if (event.key === 'Escape') setShowForm(false);
        }}>
          <button type="button" className="fixed inset-0 bg-slate-950/45 backdrop-blur-sm" onClick={() => setShowForm(false)} aria-label="Close user form" tabIndex={-1} />
          <div role="dialog" aria-modal="true" aria-labelledby="new-user-title" className="relative z-10 my-auto w-full max-w-2xl overflow-hidden rounded-lg border border-white/70 bg-white shadow-2xl">
            <form onSubmit={handleAddUser}>
              <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/80 px-5 py-4 sm:px-6">
                <span className="grid size-10 place-items-center rounded-lg bg-violet-100 text-violet-700"><ShieldCheck size={19} /></span>
                <div className="min-w-0 flex-1"><h2 id="new-user-title" className="text-base font-bold text-slate-900">Add a team member</h2><p className="mt-0.5 text-xs text-slate-500">Create an account and choose their CRM role.</p></div>
                <button type="button" onClick={() => setShowForm(false)} className="grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="Close user form"><X size={18} /></button>
              </div>
              <div className="grid gap-4 p-5 sm:grid-cols-2 sm:p-6">
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Full name <span className="text-rose-500">*</span>
                  <input autoFocus type="text" name="fullName" placeholder="e.g. Jordan Lee" value={form.fullName} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Work email <span className="text-rose-500">*</span>
                  <span className="relative block"><Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" /><input type="email" name="email" placeholder="name@company.com" value={form.email} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required /></span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Temporary password <span className="text-rose-500">*</span>
                  <input type="password" name="password" placeholder="Set a temporary password" value={form.password} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">CRM role
                  <select name="role" value={form.role} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100">
                    {TEAM_ROLE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                  </select>
                </label>
                <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4 sm:col-span-2"><Button variant="outline" onClick={() => setShowForm(false)}>Cancel</Button><Button type="submit" icon={Plus}>Add user</Button></div>
              </div>
            </form>
          </div>
        </div>
      )}

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div><h2 className="text-base font-bold text-slate-900">Team members</h2><p className="mt-1 text-xs text-slate-500">Manage access and roles across your team.</p></div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="relative min-w-48 flex-1 sm:flex-none"><Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" /><span className="sr-only">Search by name or email</span><input type="search" placeholder="Search team members" value={search} onChange={(e) => setSearch(e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" /></label>
            <label className="sr-only" htmlFor="user-role-filter">Filter by role</label>
          <select
            id="user-role-filter"
            name="role"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
          >
            <option value="">All roles</option>
            {ROLE_OPTIONS.map((option) => (
              // value is the internal role name, label is the display name
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          </div>
        </div>

        {loading ? (
          <div className="space-y-3 p-5" role="status" aria-label="Loading users">{[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}</div>
        ) : visibleUsers.length === 0 ? (
          <div className="px-5 py-14 text-center"><span className="mx-auto grid size-12 place-items-center rounded-xl bg-violet-50 text-violet-700"><UserRound size={22} /></span><h3 className="mt-4 text-base font-bold text-slate-900">{search || roleFilter ? 'No matching users' : 'No users to show'}</h3><p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{search || roleFilter ? 'Try changing your search or role filter.' : canManage ? 'Add a team member and assign them a CRM role to get started.' : 'Users in your team will be listed here.'}</p>{canManage && !search && !roleFilter && <Button className="mt-5" onClick={() => setShowForm(true)} icon={Plus}>Add first user</Button>}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-5 py-3 font-semibold">Name</th><th className="px-4 py-3 font-semibold">Email</th><th className="px-4 py-3 font-semibold">Role</th><th className="px-4 py-3 font-semibold">Status</th></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {visibleUsers.map((u) => (
                  <tr key={u.id} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-5 py-3.5"><span className="flex items-center gap-3"><span className="grid size-9 shrink-0 place-items-center rounded-lg bg-violet-50 text-sm font-bold text-violet-700">{u.fullName?.trim()?.charAt(0)?.toUpperCase() || <UserRound size={17} />}</span><span className="font-semibold text-slate-800">{u.fullName}</span></span></td>
                    <td className="px-4 py-3.5 text-slate-600">{u.email}</td>
                    <td className="px-4 py-3.5"><span className="inline-flex rounded-full bg-violet-50 px-2.5 py-1 text-xs font-semibold text-violet-700">{ROLE_LABELS[u.role] || u.role}</span></td>
                    <td className="px-4 py-3.5"><span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${u.status === 'active' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}><span className={`size-1.5 rounded-full ${u.status === 'active' ? 'bg-emerald-500' : 'bg-slate-400'}`} />{u.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && visibleUsers.length > 0 && <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">Showing {visibleUsers.length} {visibleUsers.length === 1 ? 'team member' : 'team members'}</div>}
      </section>
    </section>
  );
};

export default Users;
