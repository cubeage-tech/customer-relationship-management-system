import { useState } from 'react';
import { ListChecks } from 'lucide-react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { createMeetingTasks, findMeetingActionItems, getTaskAssignees } from '../../core/services/task.service';

const inputClass = 'border border-slate-300 rounded px-2 py-1';

/**
 * FR-AI-7.3: the AI finds the action items in the meeting's conversation; the user edits, keeps or
 * drops each (title, who, when) and only then are they created as follow-up tasks.
 */
const MeetingActionItems = ({ meeting }) => {
  const [items, setItems] = useState(null);
  const [assignees, setAssignees] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');

  const find = async () => {
    setBusy(true);
    setError('');
    setDone('');
    try {
      const [proposed, people] = await Promise.all([findMeetingActionItems(meeting.id), getTaskAssignees().catch(() => [])]);
      setAssignees(people ?? []);
      setItems((proposed ?? []).map((p, i) => ({ ...p, key: i, keep: true, assigneeId: String(p.assigneeId), dueDate: p.dueDate || '' })));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not find the action items.'));
    } finally {
      setBusy(false);
    }
  };

  const change = (key, patch) => setItems((list) => list.map((it) => (it.key === key ? { ...it, ...patch } : it)));
  const kept = (items ?? []).filter((it) => it.keep && it.title.trim());

  const create = async () => {
    setBusy(true);
    setError('');
    try {
      const created = await createMeetingTasks(meeting.id, kept.map((it) => ({
        title: it.title.trim(), notes: it.notes || undefined, dueDate: it.dueDate || undefined, assigneeId: Number(it.assigneeId),
      })));
      setItems(null);
      setDone(`${created.length} task${created.length === 1 ? '' : 's'} created — see Tasks on the ${meeting.customerId ? 'customer' : 'lead'}.`);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not create the tasks.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mt-2 border-t border-slate-200 pt-2">
      {!items && (
        <Button size="sm" variant="outline" icon={ListChecks} disabled={busy} onClick={find}>{busy ? 'Finding…' : 'Find action items'}</Button>
      )}
      {done && <p className="mt-1 text-emerald-700">{done}</p>}
      {error && <p className="mt-1 text-red-600">{error}</p>}
      {items && (
        <div className="space-y-2">
          <p className="text-xs text-slate-500">
            {items.length === 0 ? 'The AI found no action items in this conversation.' : 'Proposed by AI — edit, untick what you don\'t need, then create the tasks.'}
          </p>
          {items.map((it) => (
            <div key={it.key} className={`rounded border border-slate-200 bg-white p-2 ${it.keep ? '' : 'opacity-50'}`}>
              <div className="flex items-start gap-2">
                <input type="checkbox" className="mt-2" checked={it.keep} onChange={(e) => change(it.key, { keep: e.target.checked })} aria-label="Keep this item" />
                <div className="flex flex-1 flex-wrap items-end gap-2">
                  <input value={it.title} onChange={(e) => change(it.key, { title: e.target.value })} maxLength={200} className={`${inputClass} min-w-48 flex-1`} aria-label="Task" />
                  {assignees.length > 0 ? (
                    <select value={it.assigneeId} onChange={(e) => change(it.key, { assigneeId: e.target.value })} className={inputClass} aria-label="Who">
                      {assignees.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                    </select>
                  ) : <span className="text-xs text-slate-600">{it.assigneeName}</span>}
                  <input type="date" value={it.dueDate} onChange={(e) => change(it.key, { dueDate: e.target.value })} className={inputClass} aria-label="Due" />
                </div>
              </div>
              {it.notes && <p className="ml-6 mt-1 text-xs text-slate-500">{it.notes}</p>}
              {it.customerDoes && <p className="ml-6 text-xs text-slate-400">The customer does this — the task is to follow up.</p>}
            </div>
          ))}
          <div className="flex gap-2">
            {items.length > 0 && <Button size="sm" disabled={busy || kept.length === 0} onClick={create}>Create {kept.length} task{kept.length === 1 ? '' : 's'}</Button>}
            <Button size="sm" variant="outline" onClick={() => setItems(null)}>Close</Button>
          </div>
        </div>
      )}
    </div>
  );
};

export default MeetingActionItems;
