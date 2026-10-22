import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { INDUSTRY_LABELS, INDUSTRY_OPTIONS, LEAD_SOURCE_LABELS, LEAD_SOURCE_OPTIONS, USER_ROLES } from '../../core/constants/app.constant';
import {
  createAssignmentRule,
  deleteAssignmentRule,
  listAssignmentRules,
  updateAssignmentRule,
} from '../../core/services/lead.service';
import { listUsers } from '../../core/services/user.service';
import { listSalesTeams } from '../../core/services/salesTeam.service';

const EMPTY_FORM = { name: '', priority: '100', matchSource: '', matchIndustry: '', target: 'team', assignId: '' };
const ASSIGNABLE_ROLES = [USER_ROLES.SALES_EXECUTIVE, USER_ROLES.SALES_MANAGER];

const toPayload = (rule, overrides = {}) => ({
  name: rule.name,
  priority: rule.priority,
  active: rule.active,
  matchSource: rule.matchSource,
  matchIndustry: rule.matchIndustry,
  assignUserId: rule.assignUserId,
  assignTeamId: rule.assignTeamId,
  ...overrides,
});

/**
 * FR-2.3: tenant admin's auto-assignment rules. New leads created without an owner (except a sales
 * executive's own) go to the first active matching rule's user, or round-robin within its team.
 */
const LeadAssignmentRules = () => {
  const [rules, setRules] = useState([]);
  const [users, setUsers] = useState([]);
  const [teams, setTeams] = useState([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const reload = () => listAssignmentRules().then((data) => setRules(data ?? []));

  useEffect(() => {
    reload().catch((err) => setError(apiErrorMessage(err, 'Could not load assignment rules.')));
    listUsers().then((data) => setUsers((data ?? []).filter((u) => ASSIGNABLE_ROLES.includes(u.role)))).catch(() => setUsers([]));
    listSalesTeams().then((data) => setTeams(data ?? [])).catch(() => setTeams([]));
  }, []);

  const run = async (action, fallback) => {
    setBusy(true);
    setError('');
    try {
      await action();
      await reload();
      return true;
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const handleChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value, ...(e.target.name === 'target' ? { assignId: '' } : {}) }));

  const add = async (e) => {
    e.preventDefault();
    const id = Number(form.assignId);
    const payload = {
      name: form.name,
      priority: Number(form.priority) || 100,
      matchSource: form.matchSource || null,
      matchIndustry: form.matchIndustry || null,
      assignUserId: form.target === 'user' ? id : null,
      assignTeamId: form.target === 'team' ? id : null,
    };
    if (await run(() => createAssignmentRule(payload), 'Could not add this rule.')) setForm(EMPTY_FORM);
  };

  const describe = (rule) => {
    const when = [
      rule.matchSource && `source is ${LEAD_SOURCE_LABELS[rule.matchSource] || rule.matchSource}`,
      rule.matchIndustry && `industry is ${INDUSTRY_LABELS[rule.matchIndustry] || rule.matchIndustry}`,
    ].filter(Boolean);
    const then = rule.assignTeamId ? `rotate within team ${rule.assignTeamName}` : `assign to ${rule.assignUserName}`;
    return `${when.length ? `When ${when.join(' and ')}` : 'Any lead'} → ${then}`;
  };

  const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm';

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Lead auto-assignment</h2>
      <p className="text-sm text-slate-500 mt-1">
        New leads created without an owner go to the first active matching rule (lowest priority number first). Team rules
        take turns between the team's sales executives. A sales executive's own leads stay theirs.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <div className="mt-3 divide-y divide-slate-100">
        {rules.length === 0 && <p className="py-2 text-sm text-slate-500">No rules yet — new leads stay with their creator.</p>}
        {rules.map((rule) => (
          <div key={rule.id} className="flex flex-wrap items-center justify-between gap-3 py-2">
            <div className={`text-sm ${rule.active ? 'text-slate-800' : 'text-slate-400'}`}>
              <span className="font-medium">{rule.priority}. {rule.name}</span>
              {!rule.active && ' (inactive)'}
              <div className="text-slate-500">{describe(rule)}</div>
            </div>
            <div className="flex gap-3 text-sm">
              <button
                type="button"
                className="text-blue-600 hover:underline disabled:opacity-50"
                disabled={busy}
                onClick={() => run(() => updateAssignmentRule(rule.id, toPayload(rule, { active: !rule.active })), 'Could not update this rule.')}
              >
                {rule.active ? 'Deactivate' : 'Activate'}
              </button>
              <button
                type="button"
                className="text-red-600 hover:underline disabled:opacity-50"
                disabled={busy}
                onClick={() => run(() => deleteAssignmentRule(rule.id), 'Could not delete this rule.')}
              >
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
      <form onSubmit={add} className="flex flex-wrap items-center gap-2 mt-3">
        <input name="name" value={form.name} onChange={handleChange} placeholder="Rule name" maxLength={100} className={inputClass} aria-label="Rule name" required />
        <input name="priority" type="number" min="0" value={form.priority} onChange={handleChange} className={`${inputClass} w-20`} aria-label="Priority" />
        <select name="matchSource" value={form.matchSource} onChange={handleChange} className={inputClass} aria-label="Lead source">
          <option value="">Any source</option>
          {LEAD_SOURCE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select name="matchIndustry" value={form.matchIndustry} onChange={handleChange} className={inputClass} aria-label="Lead industry">
          <option value="">Any industry</option>
          {INDUSTRY_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select name="target" value={form.target} onChange={handleChange} className={inputClass} aria-label="Assign to">
          <option value="team">Team (round-robin)</option>
          <option value="user">User</option>
        </select>
        <select name="assignId" value={form.assignId} onChange={handleChange} className={inputClass} aria-label={form.target === 'team' ? 'Team' : 'User'} required>
          <option value="">{form.target === 'team' ? 'Choose team' : 'Choose user'}</option>
          {(form.target === 'team' ? teams : users).map((t) => (
            <option key={t.id} value={t.id}>{t.name || t.fullName}</option>
          ))}
        </select>
        <Button type="submit" disabled={busy}>Add rule</Button>
      </form>
    </div>
  );
};

export default LeadAssignmentRules;
