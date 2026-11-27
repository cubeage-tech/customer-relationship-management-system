import { useCallback, useEffect, useState } from 'react';
import Button from '../common/Button';
import { ROLE_LABELS } from '../../core/constants/app.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listRolePermissions, updateRolePermissions } from '../../core/services/rolePermission.service';

const AREAS = {
  customers: 'Customers', leads: 'Leads', opportunities: 'Opportunities', quotations: 'Quotations',
  serviceTickets: 'Service tickets', campaigns: 'Campaigns', reports: 'Reports', users: 'Users',
  settings: 'Settings', audit: 'Audit trail', billing: 'Billing', teams: 'Teams', communications: 'Communications',
};
const ACTIONS = {
  view: 'view', create: 'create', edit: 'edit', delete: 'delete or archive', assign: 'assign', approve: 'approve',
  approveDiscount: 'approve discounts', resolve: 'resolve', manage: 'manage', send: 'send, call and schedule',
  sales: 'sales reports', campaign: 'campaign reports', service: 'service reports', revenue: 'revenue reports',
};
const label = (p) => {
  const [area, action] = p.split('.');
  return `${AREAS[area] || area}: ${ACTIONS[action] || action}`;
};

/**
 * ROLE-8: the tenant admin switches individual permissions off (or back on) for each role. The
 * built-in matrix is the ceiling; the admin role itself always keeps everything.
 */
const RolePermissionEditor = () => {
  const [roles, setRoles] = useState([]);
  const [drafts, setDrafts] = useState({});
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [busy, setBusy] = useState('');

  const load = useCallback(() => listRolePermissions()
    .then((data) => {
      setRoles((data ?? []).filter((r) => r.customizable));
      setDrafts({});
    })
    .catch((err) => setError(apiErrorMessage(err, 'Could not load role permissions.'))), []);

  useEffect(() => {
    load();
  }, [load]);

  const revokedFor = (r) => drafts[r.role] ?? r.revoked;
  const toggle = (r, p) => {
    const current = revokedFor(r);
    setDrafts({ ...drafts, [r.role]: current.includes(p) ? current.filter((x) => x !== p) : [...current, p] });
    setSaved('');
  };

  const save = async (r) => {
    setBusy(r.role);
    setError('');
    try {
      await updateRolePermissions(r.role, revokedFor(r));
      await load();
      setSaved(`${ROLE_LABELS[r.role] || r.role} saved. People in this role see the change the next time their page loads.`);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save these permissions.'));
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mt-6">
      <h2 className="font-semibold text-slate-900">Customise permissions</h2>
      <p className="text-sm text-slate-500 mt-1">
        Switch off what a role shouldn&apos;t do in your organisation. Each role starts from the standard matrix above and can&apos;t go
        beyond it; the Admin role always keeps full access.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      {saved && <p className="text-sm text-green-700 mt-3">{saved}</p>}
      <div className="mt-4 space-y-5">
        {roles.map((r) => {
          const revoked = revokedFor(r);
          const dirty = drafts[r.role] !== undefined;
          return (
            <fieldset key={r.role} className="border-t border-slate-100 pt-4">
              <legend className="sr-only">{ROLE_LABELS[r.role] || r.role}</legend>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-medium text-slate-900">
                  {ROLE_LABELS[r.role] || r.role}
                  <span className="text-slate-400 font-normal text-sm"> · {r.defaults.length - revoked.length} of {r.defaults.length} on</span>
                </h3>
                <Button size="sm" disabled={!dirty || busy === r.role} onClick={() => save(r)}>{busy === r.role ? 'Saving…' : 'Save'}</Button>
              </div>
              <div className="grid gap-x-6 gap-y-1.5 sm:grid-cols-2 lg:grid-cols-3 mt-2 text-sm">
                {r.defaults.map((p) => (
                  <label key={p} className="flex items-center gap-2">
                    <input type="checkbox" checked={!revoked.includes(p)} onChange={() => toggle(r, p)} />
                    <span className={revoked.includes(p) ? 'text-slate-400 line-through' : 'text-slate-700'}>{label(p)}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          );
        })}
      </div>
    </div>
  );
};

export default RolePermissionEditor;
