import { useEffect, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listCampaigns } from '../../core/services/campaign.service';
import { createPromotion, listPromotionRedemptions, listPromotions, updatePromotion } from '../../core/services/marketing.service';

const EMPTY = { code: '', name: '', description: '', discountType: 'percent', discountValue: '', validFrom: '', validUntil: '', maxRedemptions: '', campaignId: '', active: true };
const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

const offer = (p) => (p.discountType === 'percent' ? `${Number(p.discountValue)}% off` : `₹${Number(p.discountValue).toLocaleString('en-IN')} off`);

const toPayload = (f) => ({
  code: f.code, name: f.name, description: f.description, discountType: f.discountType, discountValue: Number(f.discountValue),
  validFrom: f.validFrom || null, validUntil: f.validUntil || null, maxRedemptions: f.maxRedemptions ? Number(f.maxRedemptions) : null,
  campaignId: f.campaignId ? Number(f.campaignId) : null, active: f.active,
});

/** FR-7.3: promotions/offers and who redeemed them (redemptions are recorded on the customer page). */
const Promotions = () => {
  const { can } = usePermissions();
  const canManage = can(PERMISSIONS.CAMPAIGNS_MANAGE);

  const [promotions, setPromotions] = useState([]);
  const [campaigns, setCampaigns] = useState([]);
  const [form, setForm] = useState(null);
  const [redemptions, setRedemptions] = useState(null); // { promotionId, rows }
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = () => listPromotions().then((data) => setPromotions(data ?? [])).catch((err) => setError(apiErrorMessage(err, 'Could not load promotions.')));

  useEffect(() => {
    refresh();
    if (canManage) listCampaigns({}).then((data) => setCampaigns(data ?? [])).catch(() => setCampaigns([]));
  }, [canManage]);

  const change = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
  };

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (form.id) await updatePromotion(form.id, toPayload(form));
      else await createPromotion(toPayload(form));
      setForm(null);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save this promotion.'));
    } finally {
      setBusy(false);
    }
  };

  const edit = (p) => setForm({
    id: p.id, code: p.code, name: p.name, description: p.description || '', discountType: p.discountType, discountValue: p.discountValue,
    validFrom: p.validFrom || '', validUntil: p.validUntil || '', maxRedemptions: p.maxRedemptions ?? '', campaignId: p.campaignId ?? '', active: p.active,
  });

  const showRedemptions = (p) =>
    listPromotionRedemptions(p.id).then((rows) => setRedemptions({ promotionId: p.id, rows: rows ?? [] }))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load redemptions.')));

  return (
    <section>
      <PageHeader title="Promotions" subtitle="Offers and their redemptions. Record a redemption from the customer's page."
        actions={canManage && !form && <Button onClick={() => setForm({ ...EMPTY })}>New promotion</Button>} />
      {error && <p className="form-error mb-4">{error}</p>}

      {form && (
        <form onSubmit={save} className="bg-white border border-slate-200 rounded-lg p-5 mb-6 grid gap-3 sm:grid-cols-3 text-sm">
          <label>Code<input name="code" value={form.code} onChange={change} required maxLength={40} pattern="[A-Za-z0-9_-]+" className={inputClass} placeholder="DIWALI20" /></label>
          <label className="sm:col-span-2">Name<input name="name" value={form.name} onChange={change} required maxLength={150} className={inputClass} /></label>
          <label>Discount
            <select name="discountType" value={form.discountType} onChange={change} className={inputClass}>
              <option value="percent">Percent</option>
              <option value="amount">Fixed amount (₹)</option>
            </select>
          </label>
          <label>Value<input type="number" min="0.01" step="0.01" max={form.discountType === 'percent' ? 100 : undefined} name="discountValue" value={form.discountValue} onChange={change} required className={inputClass} /></label>
          <label>Max redemptions <span className="text-slate-400">(blank = unlimited)</span><input type="number" min="1" name="maxRedemptions" value={form.maxRedemptions} onChange={change} className={inputClass} /></label>
          <label>Valid from<input type="date" name="validFrom" value={form.validFrom} onChange={change} className={inputClass} /></label>
          <label>Valid until<input type="date" name="validUntil" value={form.validUntil} onChange={change} className={inputClass} /></label>
          <label>Campaign
            <select name="campaignId" value={form.campaignId} onChange={change} className={inputClass}>
              <option value="">None</option>
              {campaigns.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </label>
          <label className="sm:col-span-3">Description<input name="description" value={form.description} onChange={change} maxLength={500} className={inputClass} /></label>
          <label className="flex items-center gap-2"><input type="checkbox" name="active" checked={form.active} onChange={change} />Active</label>
          <div className="sm:col-span-3 flex gap-2">
            <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save promotion'}</Button>
            <Button type="button" variant="outline" onClick={() => setForm(null)}>Cancel</Button>
          </div>
        </form>
      )}

      <div className="bg-white border border-slate-200 rounded-lg divide-y divide-slate-100">
        {promotions.length === 0 && <p className="p-5 text-sm text-slate-500">No promotions yet.</p>}
        {promotions.map((p) => (
          <div key={p.id} className="p-4">
            <div className="flex flex-wrap items-start justify-between gap-3 text-sm">
              <div>
                <p className="font-medium text-slate-900"><span className="font-mono">{p.code}</span> · {p.name} · {offer(p)}</p>
                <p className="text-xs text-slate-500">
                  {p.redemptions}{p.maxRedemptions ? ` / ${p.maxRedemptions}` : ''} redeemed
                  {p.validFrom || p.validUntil ? ` · valid ${p.validFrom || '…'} – ${p.validUntil || '…'}` : ''}
                  {p.campaignName ? ` · campaign ${p.campaignName}` : ''}
                  {p.redeemable ? ' · available' : ' · not available now'}
                </p>
              </div>
              <div className="flex gap-3">
                <button type="button" className="text-blue-600 hover:underline" onClick={() => (redemptions?.promotionId === p.id ? setRedemptions(null) : showRedemptions(p))}>
                  {redemptions?.promotionId === p.id ? 'Hide redemptions' : 'Redemptions'}
                </button>
                {canManage && <button type="button" className="text-blue-600 hover:underline" onClick={() => edit(p)}>Edit</button>}
              </div>
            </div>
            {redemptions?.promotionId === p.id && (
              <ul className="mt-2 text-sm text-slate-700">
                {redemptions.rows.length === 0 && <li className="text-slate-500">Not redeemed yet.</li>}
                {redemptions.rows.map((r) => (
                  <li key={r.id}>
                    {r.customerName} · {new Date(r.redeemedAt).toLocaleDateString()}
                    {r.quotationNumber ? ` · ${r.quotationNumber}` : ''}
                    {r.discountAmount != null ? ` · saved ₹${Number(r.discountAmount).toLocaleString('en-IN')}` : ''}
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </section>
  );
};

export default Promotions;
