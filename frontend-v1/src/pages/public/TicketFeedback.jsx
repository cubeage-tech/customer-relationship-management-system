import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import Button from '../../components/common/Button';
import { getPublicFeedback, submitPublicFeedback } from '../../core/services/support.service';

const SCORES = [
  { value: 1, label: 'Very poor' },
  { value: 2, label: 'Poor' },
  { value: 3, label: 'OK' },
  { value: 4, label: 'Good' },
  { value: 5, label: 'Excellent' },
];

/** FR-6.5: the customer rates a resolved service request from the emailed link (no login). */
const TicketFeedback = () => {
  const { token } = useParams();
  const [ticket, setTicket] = useState(null);
  const [linkError, setLinkError] = useState('');
  const [score, setScore] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getPublicFeedback(token)
      .then(setTicket)
      .catch(() => setLinkError('This feedback link is not valid.'));
  }, [token]);

  const submit = async (e) => {
    e.preventDefault();
    if (!score) {
      setError('Choose a rating.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      setTicket(await submitPublicFeedback(token, { score, comment }));
    } catch (err) {
      setError(err.response?.data?.message || 'We could not save your feedback. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  if (linkError) {
    return <main className="mx-auto max-w-xl px-4 py-16 text-center text-slate-700">{linkError}</main>;
  }
  if (!ticket) {
    return <main className="mx-auto max-w-xl px-4 py-16" role="status" aria-label="Loading"><div className="h-32 animate-pulse rounded-lg bg-slate-100" /></main>;
  }

  return (
    <main className="mx-auto max-w-xl px-4 py-10">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-slate-500">{ticket.companyName}</p>
        <h1 className="mt-1 text-xl font-bold text-slate-900">How was our service?</h1>
        <p className="mt-2 text-sm text-slate-600">
          "{ticket.subject}"{ticket.technicianName ? ` — handled by ${ticket.technicianName}` : ''}
        </p>
        {ticket.score ? (
          <p className="mt-4 text-sm font-medium text-emerald-700" role="status">
            Thank you for your feedback ({ticket.score} / 5).
          </p>
        ) : (
          <form onSubmit={submit} className="mt-4 space-y-3 text-sm">
            <fieldset>
              <legend className="text-slate-700">Your rating</legend>
              <div className="mt-2 flex flex-wrap gap-2">
                {SCORES.map((s) => (
                  <button key={s.value} type="button" onClick={() => setScore(s.value)} aria-pressed={score === s.value}
                    className={`rounded-md border px-3 py-2 ${score === s.value ? 'border-primary bg-primary text-white' : 'border-slate-300 text-slate-700 hover:bg-slate-50'}`}>
                    {s.value} · {s.label}
                  </button>
                ))}
              </div>
            </fieldset>
            <label className="block text-slate-700">Comments <span className="text-slate-400">Optional</span>
              <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={3} maxLength={1000}
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2" />
            </label>
            {error && <p className="text-red-600" role="alert">{error}</p>}
            <Button type="submit" disabled={busy}>{busy ? 'Sending…' : 'Send feedback'}</Button>
          </form>
        )}
      </div>
    </main>
  );
};

export default TicketFeedback;
