import { useEffect, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import CampaignsNav from '../../components/campaign/CampaignsNav';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { LEAD_SOURCE_LABELS, LEAD_SOURCE_OPTIONS } from '../../core/constants/app.constant';
import { useIndustries } from '../../core/hooks/useIndustries';
import { apiErrorMessage } from '../../core/utils/apiError';
import { createSegment, deleteSegment, getSegmentCustomers, listSegments, updateSegment } from '../../core/services/marketing.service';

const EMPTY = {
  name: '', description: '', industries: [], leadSource: '', purchasedWithinDays: '', notPurchasedWithinDays: '',
  minPurchaseValue: '', contactedWithinDays: '', notContactedWithinDays: '',
};
const NUMBER_FIELDS = ['purchasedWithinDays', 'notPurchasedWithinDays', 'minPurchaseValue', 'contactedWithinDays', 'notContactedWithinDays'];
const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

const toForm = (s) => ({
  id: s.id, name: s.name, description: s.description || '', industries: s.industries || [], leadSource: s.leadSource || '',
  ...Object.fromEntries(NUMBER_FIELDS.map((f) => [f, s[f] ?? ''])),
});

const toPayload = (f) => ({
  name: f.name,
  description: f.description,
  industries: f.industries,
  leadSource: f.leadSource || null,
  ...Object.fromEntries(NUMBER_FIELDS.map((k) => [k, f[k] === '' ? null : Number(f[k])])),
});

/** FR-7.2: saved customer segments for campaigns — industry, purchase history, engagement and lead source. */
const Segments = () => {
  const { can } = usePermissions();
  const canManage = can(PERMISSIONS.CAMPAIGNS_MANAGE);
  const { options: industryOptions, labelOf: industryLabel } = useIndustries();

  const [segments, setSegments] = useState([]);
  const [form, setForm] = useState(null);
  const [preview, setPreview] = useState(null); // { segmentId, members }
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = () => listSegments().then((data) => setSegments(data ?? [])).catch((err) => setError(apiErrorMessage(err, 'Could not load segments.')));

  useEffect(() => {
    refresh();
  }, []);

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  const toggleIndustry = (code) => setForm((prev) => ({
    ...prev,
    industries: prev.industries.includes(code) ? prev.industries.filter((c) => c !== code) : [...prev.industries, code],
  }));

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (form.id) await updateSegment(form.id, toPayload(form));
      else await createSegment(toPayload(form));
      setForm(null);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save this segment.'));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (s) => {
    if (!window.confirm(`Delete the segment "${s.name}"?`)) return;
    try {
      await deleteSegment(s.id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not delete this segment.'));
    }
  };

  const showMembers = (s) =>
    getSegmentCustomers(s.id).then((members) => setPreview({ segmentId: s.id, members: members ?? [] }))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load the customers.')));

  const describe = (s) => [
    s.industries?.length ? `Industry: ${s.industries.map(industryLabel).join(', ')}` : null,
    s.leadSource ? `From ${LEAD_SOURCE_LABELS[s.leadSource] || s.leadSource} leads` : null,
    s.purchasedWithinDays ? `Bought in last ${s.purchasedWithinDays} days` : null,
    s.notPurchasedWithinDays ? `No purchase for ${s.notPurchasedWithinDays} days` : null,
    s.minPurchaseValue != null ? `Spent at least ₹${Number(s.minPurchaseValue).toLocaleString('en-IN')}` : null,
    s.contactedWithinDays ? `Contacted in last ${s.contactedWithinDays} days` : null,
    s.notContactedWithinDays ? `Not contacted for ${s.notContactedWithinDays} days` : null,
  ].filter(Boolean).join(' · ') || 'All active customers';

  return (
    <section>
      <PageHeader title="Segments" subtitle="Groups of customers to target with campaigns. Counts update live."
        actions={canManage && !form && <Button onClick={() => setForm({ ...EMPTY })}>New segment</Button>} />
      <CampaignsNav />
      {error && <p className="form-error mb-4">{error}</p>}

      {form && (
        <form onSubmit={save} className="bg-white border border-slate-200 rounded-lg p-5 mb-6 grid gap-3 sm:grid-cols-2 text-sm">
          <label>Name<input name="name" value={form.name} onChange={change} required maxLength={120} className={inputClass} /></label>
          <label>Description<input name="description" value={form.description} onChange={change} maxLength={500} className={inputClass} /></label>
          <fieldset className="sm:col-span-2">
            <legend className="text-slate-700">Industry <span className="text-slate-400">(any of; none = all)</span></legend>
            <div className="mt-1 flex flex-wrap gap-2">
              {industryOptions.map((o) => (
                <label key={o.value} className="flex items-center gap-1 rounded border border-slate-200 px-2 py-1">
                  <input type="checkbox" checked={form.industries.includes(o.value)} onChange={() => toggleIndustry(o.value)} />{o.label}
                </label>
              ))}
            </div>
          </fieldset>
          <label>Came from leads with source
            <select name="leadSource" value={form.leadSource} onChange={change} className={inputClass}>
              <option value="">Any</option>
              {LEAD_SOURCE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </label>
          <label>Lifetime purchases at least (₹)<input type="number" min="0" name="minPurchaseValue" value={form.minPurchaseValue} onChange={change} className={inputClass} /></label>
          <label>Bought within the last … days<input type="number" min="1" name="purchasedWithinDays" value={form.purchasedWithinDays} onChange={change} className={inputClass} /></label>
          <label>Not bought for … days<input type="number" min="1" name="notPurchasedWithinDays" value={form.notPurchasedWithinDays} onChange={change} className={inputClass} /></label>
          <label>Contacted within the last … days<input type="number" min="1" name="contactedWithinDays" value={form.contactedWithinDays} onChange={change} className={inputClass} /></label>
          <label>Not contacted for … days<input type="number" min="1" name="notContactedWithinDays" value={form.notContactedWithinDays} onChange={change} className={inputClass} /></label>
          <p className="sm:col-span-2 text-xs text-slate-500">Purchases are won deals and approved quotations; contact is any logged communication.</p>
          <div className="sm:col-span-2 flex gap-2">
            <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save segment'}</Button>
            <Button type="button" variant="outline" onClick={() => setForm(null)}>Cancel</Button>
          </div>
        </form>
      )}

      <div className="bg-white border border-slate-200 rounded-lg divide-y divide-slate-100">
        {segments.length === 0 && <p className="p-5 text-sm text-slate-500">No segments yet.</p>}
        {segments.map((s) => (
          <div key={s.id} className="p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="text-sm">
                <p className="font-medium text-slate-900">{s.name} <span className="font-normal text-slate-500">· {s.customerCount} customers</span></p>
                {s.description && <p className="text-slate-600">{s.description}</p>}
                <p className="text-xs text-slate-500 mt-1">{describe(s)}</p>
              </div>
              <div className="flex gap-3 text-sm">
                <button type="button" className="text-blue-600 hover:underline" onClick={() => (preview?.segmentId === s.id ? setPreview(null) : showMembers(s))}>
                  {preview?.segmentId === s.id ? 'Hide customers' : 'Show customers'}
                </button>
                {canManage && <button type="button" className="text-blue-600 hover:underline" onClick={() => setForm(toForm(s))}>Edit</button>}
                {canManage && <button type="button" className="text-red-600 hover:underline" onClick={() => remove(s)}>Delete</button>}
              </div>
            </div>
            {preview?.segmentId === s.id && (
              <ul className="mt-2 text-sm text-slate-700 grid gap-1 sm:grid-cols-2">
                {preview.members.length === 0 && <li className="text-slate-500">No customers match right now.</li>}
                {preview.members.map((m) => (
                  <li key={m.id}>{m.companyName} <span className="text-xs text-slate-500">{m.email || 'no email'}{m.optedOut ? ' · unsubscribed' : ''}</span></li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </section>
  );
};

export default Segments;
