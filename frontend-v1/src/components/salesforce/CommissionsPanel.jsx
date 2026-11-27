import { useEffect, useState } from 'react';
import { apiErrorMessage } from '../../core/utils/apiError';
import { approveCommission, listCommissions, markCommissionPaid } from '../../core/services/salesforce.service';

const STATUSES = ['', 'pending', 'approved', 'paid', 'void'];
const money = (v) => `₹${Number(v ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
const STATUS_CLASS = { pending: 'bg-amber-50 text-amber-700', approved: 'bg-blue-50 text-blue-700', paid: 'bg-emerald-50 text-emerald-700', void: 'bg-slate-100 text-slate-500' };

/** FR-8.5/AC-8.2: commissions recorded when deals are won; finance/admin approve and mark them paid. */
const CommissionsPanel = ({ canSettle }) => {
  const [status, setStatus] = useState('');
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');

  const refresh = () => listCommissions(status ? { status } : {}).then((data) => setRows(data ?? []))
    .catch((err) => setError(apiErrorMessage(err, 'Could not load commissions.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  const act = async (fn, id) => {
    setError('');
    try {
      await fn(id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not update this commission.'));
    }
  };

  const total = rows.filter((r) => r.status !== 'void').reduce((sum, r) => sum + Number(r.amount), 0);

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-900">Commissions <span className="font-normal text-slate-500">· {money(total)}</span></h2>
        <select value={status} onChange={(e) => setStatus(e.target.value)} className="border border-slate-300 rounded px-2 py-1 text-sm" aria-label="Status">
          {STATUSES.map((s) => <option key={s} value={s}>{s ? s[0].toUpperCase() + s.slice(1) : 'All statuses'}</option>)}
        </select>
      </div>
      <p className="text-sm text-slate-500 mt-1">Recorded automatically when a deal is marked Won; re-opening the deal voids it.</p>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
      {rows.length === 0 ? <p className="mt-3 text-sm text-slate-500">No commissions yet.</p> : (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs text-slate-500">
              <tr><th className="py-1.5">Won</th><th>Salesperson</th><th>Deal</th><th>Rule</th><th>Commission</th><th>Status</th>{canSettle && <th />}</tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-b border-slate-100">
                  <td className="py-1.5">{new Date(r.wonAt).toLocaleDateString()}</td>
                  <td>{r.userName}</td>
                  <td>{r.customerName}{r.productService ? ` · ${r.productService}` : ''}<span className="block text-xs text-slate-500">{money(r.dealValue)}</span></td>
                  <td>{r.ruleName} <span className="text-xs text-slate-500">{Number(r.ratePercent)}%</span></td>
                  <td className="font-semibold">{money(r.amount)}</td>
                  <td><span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASS[r.status] || ''}`}>{r.status}</span></td>
                  {canSettle && (
                    <td className="text-right">
                      {r.status === 'pending' && <button type="button" className="text-blue-600 hover:underline" onClick={() => act(approveCommission, r.id)}>Approve</button>}
                      {r.status === 'approved' && <button type="button" className="text-blue-600 hover:underline" onClick={() => act(markCommissionPaid, r.id)}>Mark paid</button>}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default CommissionsPanel;
