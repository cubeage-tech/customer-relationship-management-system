import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ListChecks } from 'lucide-react';
import Button from '../common/Button';
import RoutePath from '../../core/constants/routes.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { createTask, getTaskAssignees, listTasks, updateTask } from '../../core/services/task.service';

const inputClass = 'border border-slate-300 rounded px-2 py-1';
const emptyForm = () => ({ title: '', dueDate: '', assigneeId: '' });

/**
 * FR-AI-7.3: follow-up tasks — on a customer, lead or deal page (all I may see for it, with "Add task"),
 * or my own open tasks (no record given). Ticking one marks it done; tasks from a meeting say so.
 */
const TasksPanel = ({ customerId, leadId, opportunityId, canEdit = true }) => {
  const mine = !customerId && !leadId && !opportunityId;
  const scope = customerId ? { customerId } : leadId ? { leadId } : opportunityId ? { opportunityId } : {};
  const [tasks, setTasks] = useState([]);
  const [showDone, setShowDone] = useState(false);
  const [assignees, setAssignees] = useState([]);
  const [form, setForm] = useState(null);
  const [error, setError] = useState('');

  const refresh = () => listTasks(mine ? { status: 'open' } : scope).then((d) => setTasks(d ?? []))
    .catch((err) => setError(apiErrorMessage(err, 'Could not load the tasks.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId, leadId, opportunityId]);

  const openForm = () => {
    setForm(emptyForm());
    if (assignees.length === 0) getTaskAssignees().then((d) => setAssignees(d ?? [])).catch(() => setAssignees([]));
  };

  const toggle = async (t) => {
    try {
      await updateTask(t.id, { status: t.status === 'done' ? 'open' : 'done' });
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not update the task.'));
    }
  };

  const add = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createTask({ ...scope, title: form.title, dueDate: form.dueDate || undefined, assigneeId: form.assigneeId ? Number(form.assigneeId) : undefined });
      setForm(null);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not add the task.'));
    }
  };

  const shown = tasks.filter((t) => showDone || t.status === 'open');
  const closed = tasks.length - tasks.filter((t) => t.status === 'open').length;
  const recordLink = (t) => (t.customerId ? { to: RoutePath.EDIT_CUSTOMER.replace(':id', t.customerId), label: t.customerName }
    : t.leadId ? { to: RoutePath.EDIT_LEAD.replace(':id', t.leadId), label: t.leadName } : null);

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><ListChecks size={17} aria-hidden="true" /> {mine ? 'My open tasks' : 'Tasks'}</h2>
        {!mine && canEdit && !form && <Button size="sm" variant="outline" onClick={openForm}>Add task</Button>}
      </div>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      {form && (
        <form onSubmit={add} className="mt-3 flex flex-wrap items-end gap-2 text-sm">
          <label className="min-w-48 flex-1">Task
            <input value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} maxLength={200} required className={`${inputClass} w-full`} />
          </label>
          <label>Due
            <input type="date" value={form.dueDate} onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))} className={`${inputClass} block`} />
          </label>
          {assignees.length > 0 && (
            <label>For
              <select value={form.assigneeId} onChange={(e) => setForm((f) => ({ ...f, assigneeId: e.target.value }))} className={`${inputClass} block`}>
                <option value="">Me</option>
                {assignees.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </label>
          )}
          <Button size="sm" type="submit">Add</Button>
          <Button size="sm" type="button" variant="outline" onClick={() => setForm(null)}>Cancel</Button>
        </form>
      )}

      {shown.length === 0 ? (
        <p className="mt-2 text-sm text-slate-500">{mine ? 'Nothing due — tasks from meetings and records you follow up on show here.' : 'No open tasks.'}</p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100 text-sm">
          {shown.map((t) => {
            const link = mine ? recordLink(t) : null;
            return (
              <li key={t.id} className="flex items-start gap-2 py-2">
                <input type="checkbox" className="mt-1" checked={t.status === 'done'} disabled={t.status === 'cancelled'} onChange={() => toggle(t)}
                  aria-label={`Mark "${t.title}" ${t.status === 'done' ? 'not done' : 'done'}`} />
                <div className="min-w-0 flex-1">
                  <p className={t.status === 'done' ? 'text-slate-400 line-through' : 'text-slate-900'}>{t.title}</p>
                  <p className="text-xs text-slate-500">
                    {t.dueDate && <span className={t.overdue ? 'font-medium text-red-600' : ''}>due {new Date(`${t.dueDate}T00:00:00`).toLocaleDateString()}{t.overdue ? ' (overdue)' : ''} · </span>}
                    {t.assigneeName}
                    {link && <> · <Link to={link.to} className="text-primary hover:underline">{link.label}</Link></>}
                    {t.meetingTitle && ` · from meeting "${t.meetingTitle}"`}
                  </p>
                  {t.notes && <p className="text-xs text-slate-600">{t.notes}</p>}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {!mine && closed > 0 && (
        <button type="button" className="mt-1 text-xs text-primary hover:underline" onClick={() => setShowDone((v) => !v)}>
          {showDone ? 'Hide done' : `Show ${closed} done`}
        </button>
      )}
    </section>
  );
};

export default TasksPanel;
