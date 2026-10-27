import { useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { respondToQuotation } from '../../core/services/quotation.service';

/**
 * NAV-2: on the customer's quotation page — accept or decline it, with an optional comment. The
 * sales team is told at once. Calls onAnswered(updated quotation).
 */
const CustomerResponse = ({ token, onAnswered }) => {
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const answer = async (decision) => {
    setBusy(decision);
    setError('');
    try {
      onAnswered(await respondToQuotation(token, decision, comment.trim() || null));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not send your answer. Please try again.'));
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="mt-6 border-t border-slate-100 pt-4">
      <label htmlFor="quotation-comment" className="block text-sm font-medium text-slate-700">Your answer</label>
      <textarea id="quotation-comment" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} rows={2}
        placeholder="Optional — e.g. delivery date, a question, or why it doesn't suit you"
        className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
      <div className="mt-2 flex flex-wrap gap-2">
        <Button disabled={Boolean(busy)} onClick={() => answer('accept')}>{busy === 'accept' ? 'Sending…' : 'Accept quotation'}</Button>
        <Button variant="outline" disabled={Boolean(busy)} onClick={() => answer('decline')}>{busy === 'decline' ? 'Sending…' : 'Decline'}</Button>
      </div>
      {error && <p className="mt-2 text-sm text-red-600" role="alert">{error}</p>}
    </div>
  );
};

export default CustomerResponse;
