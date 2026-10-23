import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import Button from '../../components/common/Button';
import { TICKET_PRIORITY_OPTIONS } from '../../core/constants/app.constant';
import { getPublicSupportForm, submitPublicSupportRequest } from '../../core/services/support.service';

const EMPTY = { email: '', subject: '', description: '', priority: 'medium', serialNumber: '' };
const inputClass = 'mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm';

/**
 * FR-6.1: the tenant's public "Report an issue" page (no login; the link's token identifies the
 * company). The customer's email must be one the company has on record.
 */
const SupportRequest = () => {
  const { token } = useParams();
  const [companyName, setCompanyName] = useState('');
  const [linkError, setLinkError] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [ticketId, setTicketId] = useState(null);

  useEffect(() => {
    getPublicSupportForm(token)
      .then((data) => setCompanyName(data?.companyName || ''))
      .catch(() => setLinkError('This support link is not valid. Please contact the company for an up-to-date link.'));
  }, [token]);

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const result = await submitPublicSupportRequest(token, form);
      setTicketId(result?.ticketId);
    } catch (err) {
      setError(err.response?.data?.message || 'We could not send your request. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  if (linkError) {
    return <main className="mx-auto max-w-xl px-4 py-16 text-center text-slate-700">{linkError}</main>;
  }

  return (
    <main className="mx-auto max-w-xl px-4 py-10">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-slate-500">{companyName}</p>
        <h1 className="mt-1 text-xl font-bold text-slate-900">Report an issue</h1>
        {ticketId ? (
          <div className="mt-4 text-sm text-slate-700" role="status">
            <p className="font-medium text-emerald-700">Thank you — your request was received as ticket #{ticketId}.</p>
            <p className="mt-2">We've emailed you a confirmation. Our service team will be in touch.</p>
          </div>
        ) : (
          <form onSubmit={submit} className="mt-4 space-y-3 text-sm text-slate-700">
            <label className="block">Your email
              <input type="email" name="email" value={form.email} onChange={change} required maxLength={150} className={inputClass} />
              <span className="text-xs text-slate-500">Use the address you usually correspond with {companyName || 'us'} from.</span>
            </label>
            <label className="block">What's the problem?
              <input name="subject" value={form.subject} onChange={change} required maxLength={200} className={inputClass} placeholder="e.g. Machine stopped with error E4" />
            </label>
            <label className="block">Details <span className="text-slate-400">Optional</span>
              <textarea name="description" value={form.description} onChange={change} rows={4} maxLength={5000} className={inputClass} />
            </label>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block">How urgent?
                <select name="priority" value={form.priority} onChange={change} className={inputClass}>
                  {TICKET_PRIORITY_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </label>
              <label className="block">Serial number <span className="text-slate-400">Optional</span>
                <input name="serialNumber" value={form.serialNumber} onChange={change} maxLength={100} className={inputClass} />
              </label>
            </div>
            {error && <p className="text-red-600" role="alert">{error}</p>}
            <Button type="submit" disabled={busy}>{busy ? 'Sending…' : 'Send request'}</Button>
          </form>
        )}
      </div>
    </main>
  );
};

export default SupportRequest;
