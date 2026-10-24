import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { USER_ROLES } from '../../core/constants/app.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listUsers } from '../../core/services/user.service';
import { listSalesTeams } from '../../core/services/salesTeam.service';
import { createSalesTarget, deleteSalesTarget, listSalesTargets } from '../../core/services/salesforce.service';

const SALES = [USER_ROLES.SALES_EXECUTIVE, USER_ROLES.SALES_MANAGER];
const monthBounds = () => {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  return { start: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`, end: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(last)}` };
};
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm';
const money = (v) => `₹${Number(v ?? 0).toLocaleString('en-IN')}`;

/** FR-8.1/8.2/AC-8.1: targets with attainment that updates the moment deals are won. */
const TargetsPanel = ({ canManage }) => {
  const [targets, setTargets] = useState([]);
  const [people, setPeople] = useState([]);
  const [teams, setTeams] = useState([]);
  const [form, setForm] = useState(null);
  const [error, setError] = useState('');

  const refresh = () => listSalesTargets().then((data) => setTargets(data ?? [])).catch((err) => setError(apiErrorMessage(err, 'Could not load targets.')));

  useEffect(() => {
    refresh();
    if (canManage) {
      listUsers().then((data) => setPeople((data ?? []).filter((u) => SALES.includes(u.role)))).catch(() => setPeople([]));
      listSalesTeams().then((data) => setTeams(data ?? [])).catch(() => setTeams([]));
    }
  }, [canManage]);

  const open = () => {
    const { start, end } = monthBounds();
    setForm({ who: 'user', userId: '', teamId: '', metric: 'revenue', targetValue: '', periodStart: start, periodEnd: end });
  };

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const save = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createSalesTarget({
        userId: form.who === 'user' ? Number(form.userId) : null,
        teamId: form.who === 'team' ? Number(form.teamId) : null,
        metric: form.metric, targetValue: Number(form.targetValue), periodStart: form.periodStart, periodEnd: form.periodEnd,
      });
      setForm(null);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save this target.'));
    }
  };

  const remove = async (t) => {
    if (!window.confirm('Delete this target?')) return;
    try {
      await deleteSalesTarget(t.id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not delete this target.'));
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-900">Targets</h2>
        {canManage && !form && <Button onClick={open}>Set target</Button>}
      </div>
      <p className="text-sm text-slate-500 mt-1">Revenue counts won deal value; units count won deals. Progress updates as deals are won.</p>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}

      {form && (
        <form onSubmit={save} className="mt-3 flex flex-wrap items-end gap-2 rounded-md bg-slate-50 p-3">
          <select name="who" value={form.who} onChange={change} className={inputClass} aria-label="For">
            <option value="user">Salesperson</option>
            <option value="team">Team</option>
          </select>
          {form.who === 'user' ? (
            <select name="userId" value={form.userId} onChange={change} required className={inputClass} aria-label="Salesperson">
              <option value="">Choose…</option>
              {people.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
            </select>
          ) : (
            <select name="teamId" value={form.teamId} onChange={change} required className={inputClass} aria-label="Team">
              <option value="">Choose…</option>
              {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          )}
          <select name="metric" value={form.metric} onChange={change} className={inputClass} aria-label="Metric">
            <option value="revenue">Revenue (₹)</option>
            <option value="units">Units (won deals)</option>
          </select>
          <input type="number" min="1" step={form.metric === 'units' ? 1 : 0.01} name="targetValue" value={form.targetValue} onChange={change} required placeholder="Target" className={`${inputClass} w-32`} aria-label="Target" />
          <input type="date" name="periodStart" value={form.periodStart} onChange={change} required className={inputClass} aria-label="From" />
          <input type="date" name="periodEnd" value={form.periodEnd} onChange={change} required className={inputClass} aria-label="To" />
          <Button type="submit">Save</Button>
          <Button type="button" variant="outline" onClick={() => setForm(null)}>Cancel</Button>
        </form>
      )}

      {targets.length === 0 ? <p className="mt-3 text-sm text-slate-500">No targets yet.</p> : (
        <ul className="mt-3 space-y-3">
          {targets.map((t) => {
            const pct = Math.min(100, t.attainmentPercent);
            const fmt = t.metric === 'revenue' ? money : (v) => Number(v).toLocaleString();
            return (
              <li key={t.id} className="text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-slate-900">{t.userName || `Team ${t.teamName}`}
                    <span className="font-normal text-slate-500"> · {t.metric === 'revenue' ? 'Revenue' : 'Won deals'} · {t.periodStart} – {t.periodEnd}</span>
                  </span>
                  <span className="text-slate-700">
                    {fmt(t.actual)} / {fmt(t.targetValue)} · <span className="font-semibold">{t.attainmentPercent}%</span>
                    {canManage && <button type="button" className="ml-3 text-red-600 hover:underline" onClick={() => remove(t)}>Delete</button>}
                  </span>
                </div>
                <div className="mt-1 h-2 rounded bg-slate-100" role="progressbar" aria-valuenow={t.attainmentPercent} aria-valuemin={0} aria-valuemax={100}>
                  <div className={`h-2 rounded ${t.attainmentPercent >= 100 ? 'bg-emerald-500' : 'bg-primary'}`} style={{ width: `${pct}%` }} />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
};

export default TargetsPanel;
