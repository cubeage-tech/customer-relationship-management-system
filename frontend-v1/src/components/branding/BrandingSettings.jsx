import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import {
  getBranding,
  getBrandingLogoUrl,
  removeBrandingLogo,
  updateBranding,
  uploadBrandingLogo,
} from '../../core/services/branding.service';

const FIELDS = [
  { name: 'phone', label: 'Phone' },
  { name: 'email', label: 'Email', type: 'email' },
  { name: 'taxId', label: 'GSTIN / tax number' },
];

const toForm = (b) => ({
  address: b?.address || '',
  phone: b?.phone || '',
  email: b?.email || '',
  taxId: b?.taxId || '',
  color: b?.color || '',
});

/** AC-4.2: tenant admin sets the logo, brand colour and company details printed on quotation PDFs. */
const BrandingSettings = () => {
  const [form, setForm] = useState(toForm(null));
  const [hasLogo, setHasLogo] = useState(false);
  const [logoUrl, setLogoUrl] = useState(null);
  const [logoVersion, setLogoVersion] = useState(0);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getBranding()
      .then((b) => {
        setForm(toForm(b));
        setHasLogo(Boolean(b?.hasLogo));
      })
      .catch((err) => setError(apiErrorMessage(err, 'Could not load branding.')));
  }, []);

  useEffect(() => {
    if (!hasLogo) return undefined;
    let url = null;
    let cancelled = false;
    getBrandingLogoUrl().then((u) => {
      url = u;
      if (!cancelled) setLogoUrl(u);
    });
    return () => {
      cancelled = true;
      if (url) window.URL.revokeObjectURL(url);
    };
  }, [hasLogo, logoVersion]);

  const run = async (action, fallback, done) => {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const b = await action();
      setHasLogo(Boolean(b?.hasLogo));
      setLogoVersion((v) => v + 1); // re-fetch the preview after a replace
      if (done) setMessage(done);
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  };

  const save = (e) => {
    e.preventDefault();
    run(() => updateBranding(form), 'Could not save branding.', 'Branding saved. New quotation PDFs will use it.');
  };

  const onLogo = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (file) run(() => uploadBrandingLogo(file), 'Could not upload the logo.', 'Logo uploaded.');
  };

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm w-full';

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Company branding</h2>
      <p className="text-sm text-slate-500 mt-1">Printed in the header of every quotation PDF.</p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      <div className="mt-3 flex flex-wrap items-center gap-4">
        <div className="grid h-16 w-40 place-items-center rounded border border-dashed border-slate-300 bg-slate-50">
          {hasLogo && logoUrl ? <img src={logoUrl} alt="Company logo" className="max-h-14 max-w-36 object-contain" /> : <span className="text-xs text-slate-400">No logo</span>}
        </div>
        <label className="text-sm text-blue-600 hover:underline cursor-pointer">
          {hasLogo ? 'Replace logo' : 'Upload logo'}
          <input type="file" accept="image/png,image/jpeg" className="sr-only" onChange={onLogo} disabled={busy} />
        </label>
        {hasLogo && (
          <button type="button" className="text-sm text-red-600 hover:underline" disabled={busy}
            onClick={() => run(removeBrandingLogo, 'Could not remove the logo.', 'Logo removed.')}>
            Remove
          </button>
        )}
        <span className="text-xs text-slate-400">PNG or JPG, up to 1 MB</span>
      </div>

      <form onSubmit={save} className="mt-4 grid gap-3 sm:grid-cols-2">
        <label className="text-sm text-slate-600 sm:col-span-2">Address
          <textarea name="address" value={form.address} onChange={change} rows={2} maxLength={300} className={inputClass} />
        </label>
        {FIELDS.map((f) => (
          <label key={f.name} className="text-sm text-slate-600">{f.label}
            <input name={f.name} type={f.type || 'text'} value={form[f.name]} onChange={change} className={inputClass} />
          </label>
        ))}
        <label className="text-sm text-slate-600">Brand colour
          <span className="flex items-center gap-2">
            <input type="color" value={form.color || '#000000'} onChange={(e) => setForm((prev) => ({ ...prev, color: e.target.value }))} aria-label="Pick brand colour" />
            <input name="color" value={form.color} onChange={change} placeholder="#4f46e5" className={inputClass} />
          </span>
        </label>
        <div className="flex items-center gap-3 sm:col-span-2">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save branding'}</Button>
          {message && <span className="text-sm text-green-700">{message}</span>}
        </div>
      </form>
    </div>
  );
};

export default BrandingSettings;
