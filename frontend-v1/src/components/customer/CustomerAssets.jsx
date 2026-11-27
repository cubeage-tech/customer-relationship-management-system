import { useEffect, useState } from 'react';
import { Wrench } from 'lucide-react';
import Button from '../common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { createCustomerAsset, deleteCustomerAsset, listCustomerAssets, updateCustomerAsset } from '../../core/services/support.service';

const EMPTY = { name: '', model: '', serialNumber: '', installedOn: '', notes: '' };
const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

/** FR-6.1: the customer's equipment/products, which service tickets (and the support form) can reference. */
const CustomerAssets = ({ customerId }) => {
  const { can } = usePermissions();
  // Same roles that log tickets and communications may maintain the asset list.
  const canEdit = can(PERMISSIONS.COMMUNICATIONS_SEND);

  const [assets, setAssets] = useState([]);
  const [form, setForm] = useState(null); // null = closed; { id? ...fields }
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = () =>
    listCustomerAssets(customerId)
      .then((data) => setAssets(data ?? []))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load assets.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId]);

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    const payload = { ...form, installedOn: form.installedOn || null };
    try {
      if (form.id) {
        await updateCustomerAsset(customerId, form.id, payload);
      } else {
        await createCustomerAsset(customerId, payload);
      }
      setForm(null);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save this asset.'));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (asset) => {
    if (!window.confirm(`Remove ${asset.name}? Tickets that mention it keep their history.`)) return;
    try {
      await deleteCustomerAsset(customerId, asset.id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not remove this asset.'));
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Wrench size={18} aria-hidden="true" />Assets</h2>
        {canEdit && !form && <Button onClick={() => setForm({ ...EMPTY })}>Add asset</Button>}
      </div>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      {form && (
        <form onSubmit={submit} className="mt-4 grid gap-3 sm:grid-cols-2 rounded-md bg-slate-50 p-4 text-sm">
          <label>Name<input name="name" value={form.name} onChange={change} maxLength={150} required className={inputClass} placeholder="e.g. Hydraulic press" /></label>
          <label>Model<input name="model" value={form.model} onChange={change} maxLength={100} className={inputClass} /></label>
          <label>Serial number<input name="serialNumber" value={form.serialNumber} onChange={change} maxLength={100} className={inputClass} /></label>
          <label>Installed on<input type="date" name="installedOn" value={form.installedOn} onChange={change} className={inputClass} /></label>
          <label className="sm:col-span-2">Notes<textarea name="notes" value={form.notes} onChange={change} rows={2} className={inputClass} /></label>
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" disabled={busy}>{busy ? 'Saving…' : form.id ? 'Save changes' : 'Add asset'}</Button>
            <Button type="button" variant="outline" onClick={() => setForm(null)}>Close</Button>
          </div>
        </form>
      )}

      {assets.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">No assets recorded.</p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100">
          {assets.map((a) => (
            <li key={a.id} className="py-3 flex flex-wrap items-start justify-between gap-3 text-sm">
              <div>
                <p className="font-medium text-slate-900">{a.name}</p>
                <p className="text-slate-600">
                  {[a.model, a.serialNumber && `S/N ${a.serialNumber}`, a.installedOn && `installed ${new Date(a.installedOn).toLocaleDateString()}`]
                    .filter(Boolean).join(' · ') || '—'}
                </p>
                {a.notes && <p className="text-xs text-slate-500 mt-1">{a.notes}</p>}
              </div>
              {canEdit && (
                <div className="flex gap-3">
                  <button type="button" className="text-blue-600 hover:underline"
                    onClick={() => setForm({ id: a.id, name: a.name, model: a.model || '', serialNumber: a.serialNumber || '', installedOn: a.installedOn || '', notes: a.notes || '' })}>
                    Edit
                  </button>
                  <button type="button" className="text-red-600 hover:underline" onClick={() => remove(a)}>Remove</button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default CustomerAssets;
