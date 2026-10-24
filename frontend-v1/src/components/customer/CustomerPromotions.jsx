import { useEffect, useState } from 'react';
import { Tag } from 'lucide-react';
import Button from '../common/Button';
import { USER_ROLES } from '../../core/constants/app.constant';
import { usePermissions } from '../../core/hooks/usePermissions';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listCustomerPromotions, listPromotions, redeemPromotion } from '../../core/services/marketing.service';
import { listQuotations } from '../../core/services/quotation.service';

// Roles allowed to record a redemption (same as the API).
const REDEEM_ROLES = [USER_ROLES.ADMIN, USER_ROLES.MARKETING_EXECUTIVE, USER_ROLES.SALES_MANAGER, USER_ROLES.SALES_EXECUTIVE];
const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm';

/** FR-7.3: promotions this customer has used, and recording a new redemption. */
const CustomerPromotions = ({ customerId }) => {
  const { is } = usePermissions();
  const canRedeem = is(REDEEM_ROLES);

  const [rows, setRows] = useState([]);
  const [promotions, setPromotions] = useState([]);
  const [quotations, setQuotations] = useState([]);
  const [form, setForm] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = () => listCustomerPromotions(customerId).then((data) => setRows(data ?? [])).catch(() => setRows([]));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId]);

  const open = () => {
    setForm({ promotionId: '', quotationId: '', discountAmount: '', notes: '' });
    listPromotions().then((data) => setPromotions((data ?? []).filter((p) => p.redeemable))).catch(() => setPromotions([]));
    listQuotations({ customerId }).then((data) => setQuotations(data ?? [])).catch(() => setQuotations([]));
  };

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await redeemPromotion(Number(form.promotionId), {
        customerId,
        quotationId: form.quotationId ? Number(form.quotationId) : null,
        discountAmount: form.discountAmount === '' ? null : Number(form.discountAmount),
        notes: form.notes,
      });
      setForm(null);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not record the redemption.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Tag size={18} aria-hidden="true" />Promotions used</h2>
        {canRedeem && !form && <Button onClick={open}>Record redemption</Button>}
      </div>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      {form && (
        <form onSubmit={submit} className="mt-3 flex flex-wrap items-end gap-2 rounded-md bg-slate-50 p-3">
          <select name="promotionId" value={form.promotionId} onChange={change} required className={inputClass} aria-label="Promotion">
            <option value="">Choose promotion</option>
            {promotions.map((p) => <option key={p.id} value={p.id}>{p.code} — {p.name}</option>)}
          </select>
          {quotations.length > 0 && (
            <select name="quotationId" value={form.quotationId} onChange={change} className={inputClass} aria-label="Quotation">
              <option value="">No quotation</option>
              {quotations.map((q) => <option key={q.id} value={q.id}>{q.quotationNumber}</option>)}
            </select>
          )}
          <input type="number" min="0" step="0.01" name="discountAmount" value={form.discountAmount} onChange={change} placeholder="Saved (₹)" className={`${inputClass} w-28`} aria-label="Amount saved" />
          <input name="notes" value={form.notes} onChange={change} placeholder="Notes" maxLength={500} className={inputClass} aria-label="Notes" />
          <Button type="submit" disabled={busy}>Save</Button>
          <Button type="button" variant="outline" onClick={() => setForm(null)}>Cancel</Button>
        </form>
      )}

      {rows.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">No promotions used yet.</p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100 text-sm">
          {rows.map((r) => (
            <li key={r.id} className="py-2">
              <span className="font-mono font-medium">{r.promotionCode}</span> · {r.promotionName}
              <span className="text-slate-500">
                {' · '}{new Date(r.redeemedAt).toLocaleDateString()}
                {r.quotationNumber ? ` · ${r.quotationNumber}` : ''}
                {r.discountAmount != null ? ` · saved ₹${Number(r.discountAmount).toLocaleString('en-IN')}` : ''}
                {r.recordedByName ? ` · by ${r.recordedByName}` : ''}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default CustomerPromotions;
