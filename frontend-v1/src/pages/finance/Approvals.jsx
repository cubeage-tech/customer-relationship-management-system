import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import RoutePath from '../../core/constants/routes.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import {
  listQuotations,
  approveQuotationDiscount,
  rejectQuotationDiscount,
} from '../../core/services/quotation.service';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

const Approvals = () => {
  const { can } = usePermissions();

  const subtitle = can(PERMISSIONS.QUOTATIONS_APPROVE_DISCOUNT)
    ? 'Quotation discounts waiting for finance approval.'
    : 'Quotations waiting for your approval.';

  const [pending, setPending] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // The list endpoint has no discount filter, so filter the (already data-scoped) list here.
  const refresh = () =>
    listQuotations()
      .then((quotations) => setPending((quotations || []).filter((q) => q.discountApprovalStatus === 'pending')))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load quotations.')))
      .finally(() => setLoading(false));

  useEffect(() => {
    refresh();
  }, []);

  const handleApprove = async (id) => {
    setError('');
    try {
      await approveQuotationDiscount(id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not approve this discount.'));
    }
  };

  const handleReject = async (id) => {
    const reason = window.prompt('Why is this discount being rejected?');
    if (reason === null) return;
    if (!reason.trim()) {
      setError('A reason is required to reject a discount.');
      return;
    }
    setError('');
    try {
      await rejectQuotationDiscount(id, reason.trim());
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not reject this discount.'));
    }
  };

  return (
    <section>
      <PageHeader title="Approvals" subtitle={subtitle} />

      {error && <p className="form-error mb-3">{error}</p>}

      {!loading && pending.length === 0 ? (
        <EmptyState
          title="Nothing to approve"
          message="Quotations submitted for approval will be queued here."
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-5 py-3 font-semibold">Quotation #</th>
                <th className="px-4 py-3 font-semibold">Customer</th>
                <th className="px-4 py-3 font-semibold">Discount</th>
                <th className="px-4 py-3 font-semibold">Total</th>
                <th className="px-4 py-3 font-semibold">Owner</th>
                <th className="px-4 py-3 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {pending.map((q) => (
                <tr key={q.id}>
                  <td className="px-5 py-3.5">
                    <Link to={RoutePath.EDIT_QUOTATION.replace(':id', q.id)} className="font-semibold text-slate-800 hover:text-violet-700 hover:underline">
                      {q.quotationNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-3.5 text-slate-600">{q.customerName}</td>
                  <td className="px-4 py-3.5 font-semibold text-amber-600">
                    {formatCurrency(Number(q.subtotal ?? 0) - Number(q.grandTotal ?? 0))}
                  </td>
                  <td className="px-4 py-3.5 font-semibold text-slate-800">{formatCurrency(q.grandTotal)}</td>
                  <td className="px-4 py-3.5 text-slate-600">{q.ownerName || '—'}</td>
                  <td className="px-4 py-3.5">
                    <div className="flex justify-end gap-2">
                      <Button size="sm" variant="success" onClick={() => handleApprove(q.id)}>Approve</Button>
                      <Button size="sm" variant="destructive" onClick={() => handleReject(q.id)}>Reject</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default Approvals;
