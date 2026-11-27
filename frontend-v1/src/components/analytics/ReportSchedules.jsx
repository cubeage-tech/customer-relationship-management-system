import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import {
  createReportSchedule,
  deleteReportSchedule,
  listReportSchedules,
  sendReportSchedule,
  updateReportSchedule,
} from '../../core/services/analytics.service';

const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';
const FREQUENCIES = { daily: 'Daily (yesterday)', weekly: 'Weekly, Mondays (previous 7 days)', monthly: 'Monthly, on the 1st (previous month)' };
const SALES_FILTERED = ['sales', 'customers', 'revenue', 'team-performance'];

const toRequest = (s, changes = {}) => ({
  name: s.name, reportType: s.reportType, format: s.format, frequency: s.frequency, hourOfDay: s.hourOfDay,
  recipients: s.recipients, industry: s.industry, teamId: s.teamId, userId: s.userId, active: s.active, ...changes,
});

/**
 * REP-SCH: reports emailed automatically as PDF or Excel. A schedule runs as the person who set it
 * up, with their access, and only goes to users in the same company.
 * reports = [{ code, title }] the user may schedule; filters = the industry/team/user chosen above.
 */
const ReportSchedules = ({ reports, filters }) => {
  const [schedules, setSchedules] = useState([]);
  const [draft, setDraft] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refresh = () => listReportSchedules().then((data) => setSchedules(data ?? []))
    .catch((err) => setError(apiErrorMessage(err, 'Could not load report schedules.')));

  useEffect(() => {
    refresh();
  }, []);

  const run = async (action, message) => {
    setError('');
    setNotice('');
    try {
      await action();
      if (message) setNotice(message);
      refresh();
      return true;
    } catch (err) {
      setError(err?.response ? apiErrorMessage(err, 'Something went wrong.') : `Not sent: ${err.message}`);
      return false;
    }
  };

  const sendNow = (s) => run(async () => {
    const result = await sendReportSchedule(s.id);
    if (result?.lastStatus === 'failed') throw new Error(result.lastError || 'The report could not be sent.');
  }, 'Report sent.');

  const save = async (e) => {
    e.preventDefault();
    const salesFiltered = SALES_FILTERED.includes(draft.reportType);
    const body = {
      ...draft,
      hourOfDay: Number(draft.hourOfDay),
      recipients: draft.recipients.split(',').map((r) => r.trim()).filter(Boolean),
      industry: salesFiltered && filters.industry ? filters.industry : null,
      teamId: salesFiltered && filters.teamId ? Number(filters.teamId) : null,
      userId: salesFiltered && filters.userId ? Number(filters.userId) : null,
    };
    if (await run(() => createReportSchedule(body), 'Report scheduled.')) setDraft(null);
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mt-8">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
        <div>
          <h2 className="font-semibold text-slate-900">Scheduled reports</h2>
          <p className="text-sm text-slate-500">Emailed automatically as PDF or Excel, with your access to the data.</p>
        </div>
        {!draft && reports.length > 0 && (
          <Button size="sm" onClick={() => setDraft({ name: '', reportType: reports[0].code, format: 'pdf', frequency: 'weekly', hourOfDay: 7, recipients: '' })}>
            Schedule a report
          </Button>
        )}
      </div>

      {error && <p className="text-sm text-red-600 mb-3">{error}</p>}
      {notice && <p className="text-sm text-green-700 mb-3">{notice}</p>}

      {draft && (
        <form onSubmit={save} className="border border-slate-200 rounded-lg p-4 mb-4 grid gap-3 sm:grid-cols-2 text-sm">
          <label className="flex flex-col gap-1">
            <span className="text-slate-500">Name</span>
            <input required maxLength={150} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} className={inputClass} />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-slate-500">Report</span>
            <select value={draft.reportType} onChange={(e) => setDraft({ ...draft, reportType: e.target.value })} className={inputClass}>
              {reports.map((r) => <option key={r.code} value={r.code}>{r.title}</option>)}
            </select>
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-slate-500">How often</span>
            <select value={draft.frequency} onChange={(e) => setDraft({ ...draft, frequency: e.target.value })} className={inputClass}>
              {Object.entries(FREQUENCIES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <div className="flex gap-3">
            <label className="flex flex-col gap-1">
              <span className="text-slate-500">At (hour)</span>
              <input type="number" min={0} max={23} value={draft.hourOfDay} onChange={(e) => setDraft({ ...draft, hourOfDay: e.target.value })} className={`${inputClass} w-20`} />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-slate-500">Format</span>
              <select value={draft.format} onChange={(e) => setDraft({ ...draft, format: e.target.value })} className={inputClass}>
                <option value="pdf">PDF</option>
                <option value="xlsx">Excel (XLSX)</option>
              </select>
            </label>
          </div>
          <label className="flex flex-col gap-1 sm:col-span-2">
            <span className="text-slate-500">Send to (emails of people in your company, comma-separated; empty = you)</span>
            <input value={draft.recipients} onChange={(e) => setDraft({ ...draft, recipients: e.target.value })} className={inputClass} />
          </label>
          {SALES_FILTERED.includes(draft.reportType) && (
            <p className="text-xs text-slate-400 sm:col-span-2">Uses the industry, team and salesperson filters selected above.</p>
          )}
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" size="sm">Save schedule</Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setDraft(null)}>Cancel</Button>
          </div>
        </form>
      )}

      {schedules.length === 0 ? (
        <p className="text-sm text-slate-400">No reports scheduled yet.</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {schedules.map((s) => (
            <li key={s.id} className="py-3 flex flex-wrap items-center justify-between gap-3 text-sm">
              <div>
                <p className="font-medium text-slate-900">{s.name} <span className="text-slate-400 font-normal">· {s.reportTitle} · {s.format.toUpperCase()}</span></p>
                <p className="text-slate-500">
                  {FREQUENCIES[s.frequency]} at {String(s.hourOfDay).padStart(2, '0')}:00 to {s.recipients.join(', ')}
                  {s.ownerName && ` · set up by ${s.ownerName}`}
                </p>
                <p className="text-xs text-slate-400">
                  {s.active ? `Next: ${new Date(s.nextRunAt).toLocaleString()}` : 'Paused'}
                  {s.lastRunAt && ` · Last: ${new Date(s.lastRunAt).toLocaleString()} (${s.lastStatus})`}
                  {s.lastStatus === 'failed' && s.lastError && ` — ${s.lastError}`}
                </p>
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => sendNow(s)}>Send now</Button>
                <Button size="sm" variant="outline" onClick={() => run(() => updateReportSchedule(s.id, toRequest(s, { active: !s.active })))}>
                  {s.active ? 'Pause' : 'Resume'}
                </Button>
                <Button size="sm" variant="ghost" onClick={() => run(() => deleteReportSchedule(s.id), 'Schedule deleted.')}>Delete</Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default ReportSchedules;
