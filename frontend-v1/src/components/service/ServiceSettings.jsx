import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { TICKET_PRIORITY_LABELS, TICKET_PRIORITY_OPTIONS, USER_ROLES } from '../../core/constants/app.constant';
import { useIndustries } from '../../core/hooks/useIndustries';
import { listUsers } from '../../core/services/user.service';
import {
  createTicketAssignmentRule,
  deleteTicketAssignmentRule,
  getSupportFormLink,
  listTicketAssignmentRules,
  resetSupportFormLink,
  updateTicketAssignmentRule,
} from '../../core/services/support.service';

const EMPTY_RULE = { name: '', priority: '100', matchPriority: '', matchIndustry: '', assignUserId: '' };
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm';

/** FR-6.1: the shareable customer "Report an issue" link (admin). */
export const SupportFormSettings = () => {
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');

  const load = (reset) =>
    (reset ? resetSupportFormLink() : getSupportFormLink())
      .then((data) => setUrl(data?.url || ''))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load the support link.')));

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Customer support form</h2>
      <p className="text-sm text-slate-500 mt-1">
        Share this link (on your website or in emails) so customers can report an issue themselves. Requests from an email
        address on record become service tickets.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      {url ? (
        <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
          <input readOnly value={url} className={`${inputClass} flex-1 min-w-0`} aria-label="Support form link" onFocus={(e) => e.target.select()} />
          <button type="button" className="text-blue-600 hover:underline" onClick={() => navigator.clipboard?.writeText(url).catch(() => {})}>Copy</button>
          <button type="button" className="text-slate-500 hover:underline"
            onClick={() => window.confirm('Replace the link? The current one stops working.') && load(true)}>Replace link</button>
        </div>
      ) : (
        <button type="button" className="mt-3 text-sm text-blue-600 hover:underline" onClick={() => load(false)}>Show the support form link</button>
      )}
    </div>
  );
};

/** FR-6.3b: rules that auto-assign new tickets to an agent, or round-robin across all agents. */
export const TicketAssignmentRules = () => {
  const { options: industryOptions, labelOf: industryLabel } = useIndustries();
  const [rules, setRules] = useState([]);
  const [agents, setAgents] = useState([]);
  const [form, setForm] = useState(EMPTY_RULE);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const reload = () => listTicketAssignmentRules().then((data) => setRules(data ?? []));

  useEffect(() => {
    reload().catch((err) => setError(apiErrorMessage(err, 'Could not load assignment rules.')));
    listUsers().then((data) => setAgents((data ?? []).filter((u) => u.role === USER_ROLES.SERVICE_AGENT))).catch(() => setAgents([]));
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
      name: form.name,
      priority: Number(form.priority) || 100,
      matchPriority: form.matchPriority || null,
      matchIndustry: form.matchIndustry || null,
      assignUserId: form.assignUserId ? Number(form.assignUserId) : null,
    };
    if (await run(() => createTicketAssignmentRule(payload), 'Could not add this rule.')) setForm(EMPTY_RULE);
  };

  const describe = (r) => {
    const when = [
      r.matchPriority && `priority is ${TICKET_PRIORITY_LABELS[r.matchPriority] || r.matchPriority}`,
      r.matchIndustry && `customer industry is ${industryLabel(r.matchIndustry)}`,
    ].filter(Boolean);
    return `${when.length ? `When ${when.join(' and ')}` : 'Any ticket'} → ${r.assignUserName ? `assign to ${r.assignUserName}` : 'rotate across all service agents'}`;
  };

  const toPayload = (r, overrides) => ({
    name: r.name, priority: r.priority, active: r.active, matchPriority: r.matchPriority, matchIndustry: r.matchIndustry,
    assignUserId: r.assignUserId, ...overrides,
  });

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Ticket auto-assignment</h2>
      <p className="text-sm text-slate-500 mt-1">
        New tickets go to the first active matching rule (lowest number first). Tickets no rule matches stay in the agents' queue.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <div className="mt-3 divide-y divide-slate-100">
        {rules.length === 0 && <p className="py-2 text-sm text-slate-500">No rules yet — agents claim tickets from the queue.</p>}
        {rules.map((r) => (
          <div key={r.id} className="flex flex-wrap items-center justify-between gap-3 py-2">
            <div className={`text-sm ${r.active ? 'text-slate-800' : 'text-slate-400'}`}>
              <span className="font-medium">{r.priority}. {r.name}</span>{!r.active && ' (inactive)'}
              <div className="text-slate-500">{describe(r)}</div>
            </div>
            <div className="flex gap-3 text-sm">
              <button type="button" className="text-blue-600 hover:underline" disabled={busy}
                onClick={() => run(() => updateTicketAssignmentRule(r.id, toPayload(r, { active: !r.active })), 'Could not update this rule.')}>
                {r.active ? 'Deactivate' : 'Activate'}
              </button>
              <button type="button" className="text-red-600 hover:underline" disabled={busy}
                onClick={() => run(() => deleteTicketAssignmentRule(r.id), 'Could not delete this rule.')}>Delete</button>
            </div>
          </div>
        ))}
      </div>
      <form onSubmit={add} className="flex flex-wrap items-center gap-2 mt-3">
        <input name="name" value={form.name} onChange={change} placeholder="Rule name" maxLength={100} className={inputClass} aria-label="Rule name" required />
        <input name="priority" type="number" min="0" value={form.priority} onChange={change} className={`${inputClass} w-20`} aria-label="Rule order" />
        <select name="matchPriority" value={form.matchPriority} onChange={change} className={inputClass} aria-label="Ticket priority">
          <option value="">Any priority</option>
          {TICKET_PRIORITY_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select name="matchIndustry" value={form.matchIndustry} onChange={change} className={inputClass} aria-label="Customer industry">
          <option value="">Any industry</option>
          {industryOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select name="assignUserId" value={form.assignUserId} onChange={change} className={inputClass} aria-label="Assign to">
          <option value="">Round-robin (all agents)</option>
          {agents.map((a) => <option key={a.id} value={a.id}>{a.fullName}</option>)}
        </select>
        <Button type="submit" disabled={busy}>Add rule</Button>
      </form>
    </div>
  );
};
