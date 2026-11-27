import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listCustomers } from '../../core/services/customer.service';
import { currentLocation, getActivitySummary, logVisit } from '../../core/services/salesforce.service';

const iso = (d) => d.toISOString().slice(0, 10);
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm';

/**
 * FR-8.3: activity per salesperson — calls, meetings and quotations sent are counted automatically;
 * field visits are logged here (with location when allowed).
 */
const ActivitiesPanel = ({ canLog }) => {
  const [from, setFrom] = useState(() => iso(new Date(Date.now() - 6 * 86400000)));
  const [to, setTo] = useState(() => iso(new Date()));
  const [rows, setRows] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [visit, setVisit] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = () => getActivitySummary({ from, to }).then((data) => setRows(data ?? []))
    .catch((err) => setError(apiErrorMessage(err, 'Could not load activity.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [from, to]);

  const open = () => {
    setVisit({ customerId: '', notes: '' });
    listCustomers({}).then((data) => setCustomers(data ?? [])).catch(() => setCustomers([]));
  };

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const location = await currentLocation();
      await logVisit({ customerId: visit.customerId ? Number(visit.customerId) : null, notes: visit.notes, ...(location || {}) });
      setVisit(null);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not log the visit.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-900">Sales activities</h2>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className={inputClass} aria-label="From" />
          <span className="text-slate-400">to</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className={inputClass} aria-label="To" />
          {canLog && !visit && <Button onClick={open}>Log visit</Button>}
        </div>
      </div>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}

      {visit && (
        <form onSubmit={save} className="mt-3 flex flex-wrap items-end gap-2 rounded-md bg-slate-50 p-3">
          <select value={visit.customerId} onChange={(e) => setVisit((v) => ({ ...v, customerId: e.target.value }))} className={inputClass} aria-label="Customer">
            <option value="">No specific customer</option>
            {customers.map((c) => <option key={c.id} value={c.id}>{c.companyName}</option>)}
          </select>
          <input value={visit.notes} onChange={(e) => setVisit((v) => ({ ...v, notes: e.target.value }))} placeholder="What happened?" maxLength={2000} className={`${inputClass} min-w-64 flex-1`} aria-label="Notes" />
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save visit'}</Button>
          <Button type="button" variant="outline" onClick={() => setVisit(null)}>Cancel</Button>
        </form>
      )}

      <div className="mt-3 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-xs text-slate-500">
            <tr><th className="py-1.5">Salesperson</th><th>Calls</th><th>Meetings</th><th>Quotations sent</th><th>Visits</th><th>Total</th></tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.userId} className="border-b border-slate-100">
                <td className="py-1.5">{r.userName}</td><td>{r.calls}</td><td>{r.meetings}</td><td>{r.quotationsSent}</td><td>{r.visits}</td>
                <td className="font-semibold">{r.total}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default ActivitiesPanel;
