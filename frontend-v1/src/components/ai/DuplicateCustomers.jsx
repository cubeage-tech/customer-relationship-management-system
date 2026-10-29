import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Copy } from 'lucide-react';
import Button from '../common/Button';
import RoutePath from '../../core/constants/routes.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { dismissDuplicateCustomers, findDuplicateCustomers } from '../../core/services/ai.service';

const LEVEL = { likely: 'bg-red-50 text-red-700', possible: 'bg-amber-50 text-amber-800' };

const Side = ({ s }) => (
  <span className="min-w-0">
    <Link to={RoutePath.EDIT_CUSTOMER.replace(':id', s.id)} className="font-medium text-primary hover:underline">{s.name}</Link>
    <span className="block text-xs text-slate-500">{s.ownerName || 'No owner'} · added {new Date(s.createdAt).toLocaleDateString()}</span>
  </span>
);

/**
 * FR-AI-9.1: on the customer list, on request — pairs of customers that are probably the same company
 * (same email or phone, near-identical name, same website or company email domain), likeliest first,
 * with the reasons. "Not a duplicate" stops a pair being suggested again.
 */
const DuplicateCustomers = () => {
  const [pairs, setPairs] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const find = async () => {
    setBusy(true);
    setError('');
    try {
      setPairs(await findDuplicateCustomers());
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not look for duplicates.'));
    } finally {
      setBusy(false);
    }
  };

  const dismiss = async (p) => {
    try {
      await dismissDuplicateCustomers(p.first.id, p.second.id);
      setPairs((list) => list.filter((x) => x !== p));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save that.'));
    }
  };

  return (
    <section className="mb-4 rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900"><Copy size={16} aria-hidden="true" /> Possible duplicates</h2>
        <Button size="sm" variant="outline" disabled={busy} onClick={find}>{busy ? 'Looking…' : pairs ? 'Look again' : 'Find duplicates'}</Button>
      </div>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {pairs && pairs.length === 0 && <p className="mt-2 text-sm text-slate-500">No probable duplicates among the customers you can see.</p>}
      {pairs && pairs.length > 0 && (
        <ul className="mt-2 divide-y divide-slate-100 text-sm">
          {pairs.map((p) => (
            <li key={`${p.first.id}-${p.second.id}`} className="py-2">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="grid flex-1 gap-2 sm:grid-cols-2"><Side s={p.first} /><Side s={p.second} /></div>
                <div className="flex items-center gap-3">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${LEVEL[p.level]}`}>{p.level === 'likely' ? 'Likely' : 'Possible'} · {Math.round(p.score * 100)}%</span>
                  <button type="button" className="text-xs text-slate-600 hover:underline" onClick={() => dismiss(p)}>Not a duplicate</button>
                </div>
              </div>
              <p className="mt-1 text-xs text-slate-500">{p.reasons.join(' · ')}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
};

export default DuplicateCustomers;
