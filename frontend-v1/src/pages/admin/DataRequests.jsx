import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import Button from '../../components/common/Button';
import RecordPicker from '../../components/communication/RecordPicker';
import { apiErrorMessage } from '../../core/utils/apiError';
import { createDataRequest, downloadDataExport, eraseDataRequest, listDataRequests, rejectDataRequest } from '../../core/services/admin.service';

const STATUS = {
  open: 'bg-amber-50 text-amber-700',
  completed: 'bg-green-50 text-green-700',
  rejected: 'bg-slate-100 text-slate-600',
};
const RECORD = { customer: (id) => `/customers/${id}`, lead: (id) => `/leads/${id}` };

/**
 * SEC-8: data-protection requests. Log a customer's or lead's request to see (export) or delete
 * (erase) their personal data, and act on it within its 30-day due date. Erasure keeps quotations
 * and deals under an anonymous name and cannot be undone.
 */
const DataRequests = () => {
  const [rows, setRows] = useState(null);
  const [subject, setSubject] = useState(null);
  const [type, setType] = useState('export');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => listDataRequests().then((d) => setRows(d ?? []))
    .catch((err) => setError(apiErrorMessage(err, 'Could not load the requests.'))), []);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (fn) => {
    setBusy(true);
    setError('');
    try {
      await fn();
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, 'That didn\'t work.'));
    } finally {
      setBusy(false);
    }
  };

  const create = (e) => {
    e.preventDefault();
    run(async () => {
      await createDataRequest({ subjectType: subject.type, subjectId: subject.id, requestType: type, notes });
      setSubject(null);
      setNotes('');
    });
  };

  const erase = (r) => {
    const confirm = window.prompt(`Erase the personal data of ${r.subjectName}? This cannot be undone. Type ERASE to confirm.`);
    if (confirm === null) return;
    run(() => eraseDataRequest(r.id, confirm));
  };

  const reject = (r) => {
    const reason = window.prompt('Why is this request being closed without action?');
    if (reason === null) return;
    run(() => rejectDataRequest(r.id, reason));
  };

  return (
    <section>
      <PageHeader title="Data requests" subtitle="Export or erase a customer's or lead's personal data on request (due within 30 days)." />
      {error && <p className="text-sm text-red-600 mb-4" role="alert">{error}</p>}

      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <h2 className="font-semibold text-slate-900">Log a request</h2>
        {!subject ? <div className="mt-3"><RecordPicker onPick={setSubject} /></div> : (
          <form onSubmit={create} className="mt-3 flex flex-wrap items-end gap-3 text-sm">
            <p className="w-full text-slate-600">For <span className="font-medium text-slate-900">{subject.name}</span>{' '}
              <button type="button" className="text-primary underline" onClick={() => setSubject(null)}>change</button></p>
            <label className="flex flex-col gap-1">
              <span className="text-slate-500">They asked to</span>
              <select value={type} onChange={(e) => setType(e.target.value)} className="border border-slate-300 rounded px-2 py-1 bg-white">
                <option value="export">See their data (export)</option>
                <option value="erasure">Delete their data (erasure)</option>
              </select>
            </label>
            <label className="flex flex-col gap-1 flex-1 min-w-[14rem]">
              <span className="text-slate-500">Notes (how it was received)</span>
              <input value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} className="border border-slate-300 rounded px-2 py-1" />
            </label>
            <Button type="submit" size="sm" disabled={busy}>Log request</Button>
          </form>
        )}
      </div>

      {rows === null && <p className="text-sm text-slate-400">Loading…</p>}
      {rows && rows.length === 0 && <p className="text-sm text-slate-400">No requests yet.</p>}
      {rows && rows.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead><tr className="border-b border-slate-200 text-left text-slate-500">
              {['Requested', 'Who', 'Request', 'Status', 'Due', ''].map((h) => <th key={h} className="px-3 py-2 font-medium whitespace-nowrap">{h}</th>)}
            </tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-b border-slate-100 align-top">
                  <td className="px-3 py-2 whitespace-nowrap text-slate-600">{new Date(r.requestedAt).toLocaleDateString()}</td>
                  <td className="px-3 py-2"><Link to={RECORD[r.subjectType](r.subjectId)} className="text-primary hover:underline">{r.subjectName}</Link>
                    <span className="block text-xs text-slate-400">{r.subjectType}</span></td>
                  <td className="px-3 py-2 text-slate-700">{r.requestType === 'export' ? 'Export' : 'Erasure'}
                    {r.notes && <span className="block text-xs text-slate-400">{r.notes}</span>}</td>
                  <td className="px-3 py-2"><span className={`px-2 py-0.5 rounded-full text-xs font-medium capitalize ${STATUS[r.status]}`}>{r.status}</span>
                    {r.outcome && <span className="block text-xs text-slate-400 mt-1 max-w-xs">{r.outcome}</span>}</td>
                  <td className={`px-3 py-2 whitespace-nowrap ${r.overdue ? 'text-red-600 font-medium' : 'text-slate-600'}`}>
                    {new Date(r.dueAt).toLocaleDateString()}{r.overdue ? ' (overdue)' : ''}</td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      <Button size="sm" variant="outline" disabled={busy || r.subjectName?.startsWith('Erased')}
                        onClick={() => run(() => downloadDataExport(r.id, `${r.subjectType}-${r.subjectId}`))}>Download data</Button>
                      {r.status === 'open' && r.requestType === 'erasure' && <Button size="sm" variant="destructive" disabled={busy} onClick={() => erase(r)}>Erase</Button>}
                      {r.status === 'open' && <Button size="sm" variant="ghost" disabled={busy} onClick={() => reject(r)}>Close</Button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default DataRequests;
