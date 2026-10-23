import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { USER_ROLES } from '../../core/constants/app.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listUsers } from '../../core/services/user.service';
import { listSalesTeams } from '../../core/services/salesTeam.service';
import { createCommissionRule, deleteCommissionRule, listCommissionRules, updateCommissionRule } from '../../core/services/salesforce.service';

const EMPTY = { name: '', priority: '100', ratePercent: '', scope: 'all', userId: '', teamId: '', minDealValue: '' };
const SALES = [USER_ROLES.SALES_EXECUTIVE, USER_ROLES.SALES_MANAGER];
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm';

/** FR-8.5: the admin's commission rules — rate % of the won deal's value; the first matching rule applies. */
const CommissionRulesSettings = () => {
  const [rules, setRules] = useState([]);
  const [people, setPeople] = useState([]);
  const [teams, setTeams] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const reload = () => listCommissionRules().then((data) => setRules(data ?? []));

  useEffect(() => {
    reload().catch((err) => setError(apiErrorMessage(err, 'Could not load commission rules.')));
    listUsers().then((data) => setPeople((data ?? []).filter((u) => SALES.includes(u.role)))).catch(() => setPeople([]));
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

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const add = async (e) => {
    e.preventDefault();
    const payload = {
      name: form.name, priority: Number(form.priority) || 100, ratePercent: Number(form.ratePercent),
      userId: form.scope === 'user' ? Number(form.userId) : null, teamId: form.scope === 'team' ? Number(form.teamId) : null,
      minDealValue: form.minDealValue === '' ? null : Number(form.minDealValue),
    };
    if (await run(() => createCommissionRule(payload), 'Could not add this rule.')) setForm(EMPTY);
  };

  const describe = (r) => `${Number(r.ratePercent)}% of the deal · ${r.userName || (r.teamName ? `team ${r.teamName}` : 'everyone')}`
    + (r.minDealValue != null ? ` · deals from ₹${Number(r.minDealValue).toLocaleString('en-IN')}` : '');

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Commission rules</h2>
      <p className="text-sm text-slate-500 mt-1">
        When a deal is marked Won, the first active matching rule (lowest number first) records the owner's commission.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <div className="mt-3 divide-y divide-slate-100">
        {rules.length === 0 && <p className="py-2 text-sm text-slate-500">No rules — no commission is recorded.</p>}
        {rules.map((r) => (
          <div key={r.id} className="flex flex-wrap items-center justify-between gap-3 py-2 text-sm">
            <div className={r.active ? 'text-slate-800' : 'text-slate-400'}>
              <span className="font-medium">{r.priority}. {r.name}</span>{!r.active && ' (inactive)'}
              <div className="text-slate-500">{describe(r)}</div>
            </div>
            <div className="flex gap-3">
              <button type="button" className="text-blue-600 hover:underline" disabled={busy}
                onClick={() => run(() => updateCommissionRule(r.id, {
                  name: r.name, priority: r.priority, active: !r.active, ratePercent: r.ratePercent, userId: r.userId, teamId: r.teamId, minDealValue: r.minDealValue,
                }), 'Could not update this rule.')}>
                {r.active ? 'Deactivate' : 'Activate'}
              </button>
              <button type="button" className="text-red-600 hover:underline" disabled={busy}
                onClick={() => run(() => deleteCommissionRule(r.id), 'Could not delete this rule.')}>Delete</button>
            </div>
          </div>
        ))}
      </div>
      <form onSubmit={add} className="flex flex-wrap items-center gap-2 mt-3">
        <input name="name" value={form.name} onChange={change} placeholder="Rule name" maxLength={100} required className={inputClass} aria-label="Rule name" />
        <input name="priority" type="number" min="0" value={form.priority} onChange={change} className={`${inputClass} w-20`} aria-label="Rule order" />
        <input name="ratePercent" type="number" min="0.01" max="100" step="0.01" value={form.ratePercent} onChange={change} placeholder="Rate %" required className={`${inputClass} w-24`} aria-label="Rate percent" />
        <select name="scope" value={form.scope} onChange={change} className={inputClass} aria-label="Applies to">
          <option value="all">Everyone</option>
          <option value="user">One salesperson</option>
          <option value="team">One team</option>
        </select>
        {form.scope === 'user' && (
          <select name="userId" value={form.userId} onChange={change} required className={inputClass} aria-label="Salesperson">
            <option value="">Choose…</option>
            {people.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
          </select>
        )}
        {form.scope === 'team' && (
          <select name="teamId" value={form.teamId} onChange={change} required className={inputClass} aria-label="Team">
            <option value="">Choose…</option>
            {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select>
        )}
        <input name="minDealValue" type="number" min="0" value={form.minDealValue} onChange={change} placeholder="Min deal ₹ (optional)" className={`${inputClass} w-40`} aria-label="Minimum deal value" />
        <Button type="submit" disabled={busy}>Add rule</Button>
      </form>
    </div>
  );
};

export default CommissionRulesSettings;
