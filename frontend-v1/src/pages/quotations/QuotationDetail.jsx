import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import {
  QUOTATION_STATUS_LABELS,
  QUOTATION_CUSTOMER_STATUS_OPTIONS,
  DISCOUNT_APPROVAL_STATUS_LABELS,
  QUOTATION_STATUSES,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import {
  getQuotation,
  sendQuotation,
  setQuotationCustomerStatus,
  approveQuotationDiscount,
  rejectQuotationDiscount,
  downloadQuotationPdf,
} from '../../core/services/quotation.service';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

const QuotationDetail = () => {
  const { id } = useParams();
  const { can } = usePermissions();
  const canEdit = can(PERMISSIONS.QUOTATIONS_EDIT);
  const canApproveDiscount = can(PERMISSIONS.QUOTATIONS_APPROVE) || can(PERMISSIONS.QUOTATIONS_APPROVE_DISCOUNT);

  const [quotation, setQuotation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [actionError, setActionError] = useState('');

  const refresh = () => {
    getQuotation(id)
      .then(setQuotation)
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleSend = async () => {
    setActionError('');
    try {
      const saved = await sendQuotation(id);
      setQuotation(saved);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not send this quotation.');
    }
  };

  const handleCustomerStatus = async (e) => {
    const status = e.target.value;
    if (!status) return;
    const saved = await setQuotationCustomerStatus(id, status);
    setQuotation(saved);
  };

  const handleApproveDiscount = async () => {
    const saved = await approveQuotationDiscount(id);
    setQuotation(saved);
  };

  const handleRejectDiscount = async () => {
    const reason = window.prompt('Why is this discount being rejected?');
    const saved = await rejectQuotationDiscount(id, reason || '');
    setQuotation(saved);
  };

  if (loading) return null;

  if (notFound) {
    return (
      <EmptyState
        title="Quotation not found"
        message="This quotation may have been removed, or you may not have access to it."
        action={<Link to={RoutePath.QUOTATIONS} className="text-indigo-600 hover:underline">Back to quotations</Link>}
      />
    );
  }

  const discountTotal = Number(quotation.subtotal ?? 0) - Number(quotation.grandTotal ?? 0);

  return (
    <section>
      <PageHeader
        title={quotation.quotationNumber}
        subtitle={`${quotation.customerName} · ${QUOTATION_STATUS_LABELS[quotation.status] || quotation.status}`}
        actions={
          <div className="flex items-center gap-3">
            <Link to={RoutePath.QUOTATIONS} className="text-sm text-slate-500 hover:underline">
              Back to quotations
            </Link>
            <Button variant="secondary" onClick={() => downloadQuotationPdf(id, quotation.quotationNumber)}>
              Download PDF
            </Button>
          </div>
        }
      />

      {/* ---------------- Status & actions ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex flex-wrap items-center gap-6 text-sm">
          <div>
            <p className="text-slate-500">Discount approval</p>
            <p className={quotation.discountApprovalStatus === 'pending' ? 'text-amber-600 font-medium' : 'font-medium'}>
              {DISCOUNT_APPROVAL_STATUS_LABELS[quotation.discountApprovalStatus] || quotation.discountApprovalStatus}
            </p>
          </div>
          {quotation.discountReviewNote && (
            <div>
              <p className="text-slate-500">Review note</p>
              <p>{quotation.discountReviewNote}</p>
            </div>
          )}
        </div>

        {actionError && <p className="form-error mt-3">{actionError}</p>}

        <div className="mt-4 flex flex-wrap gap-3">
          {canEdit && quotation.status === QUOTATION_STATUSES.DRAFT && (
            <Button onClick={handleSend}>Send to customer</Button>
          )}

          {canApproveDiscount && quotation.discountApprovalStatus === 'pending' && (
            <>
              <Button variant="success" onClick={handleApproveDiscount}>Approve discount</Button>
              <Button variant="destructive" onClick={handleRejectDiscount}>Reject discount</Button>
            </>
          )}

          {canEdit && quotation.status !== QUOTATION_STATUSES.DRAFT && (
            <select
              onChange={handleCustomerStatus}
              defaultValue=""
              className="border border-slate-300 rounded px-2 py-1 text-sm"
            >
              <option value="" disabled>Record customer response…</option>
              {QUOTATION_CUSTOMER_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          )}
        </div>
      </div>

      {/* ---------------- Line items ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <h2 className="font-semibold text-slate-900 mb-3">Line items</h2>
        <table className="w-full text-left text-sm border-collapse">
          <thead>
            <tr className="border-b border-slate-300">
              <th className="py-2 pr-4">Product / service</th>
              <th className="py-2 pr-4">Qty</th>
              <th className="py-2 pr-4">Unit price</th>
              <th className="py-2 pr-4">Discount</th>
              <th className="py-2 pr-4">Line total</th>
            </tr>
          </thead>
          <tbody>
            {quotation.lineItems.map((item) => (
              <tr key={item.id} className="border-b border-slate-100">
                <td className="py-2 pr-4">{item.productName}</td>
                <td className="py-2 pr-4">{item.quantity}</td>
                <td className="py-2 pr-4">{formatCurrency(item.unitPrice)}</td>
                <td className="py-2 pr-4">{item.discountPercent}%</td>
                <td className="py-2 pr-4">{formatCurrency(item.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="mt-4 flex flex-col items-end text-sm gap-1">
          <p>Subtotal: {formatCurrency(quotation.subtotal)}</p>
          <p>Discount: {formatCurrency(discountTotal)}</p>
          <p className="font-semibold text-base">Grand total: {formatCurrency(quotation.grandTotal)}</p>
        </div>
      </div>

      {/* ---------------- Details ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5">
        <h2 className="font-semibold text-slate-900 mb-3">Details</h2>
        <dl className="grid gap-3 sm:grid-cols-2 text-sm">
          <div><dt className="text-slate-500">Owner</dt><dd>{quotation.ownerName || '—'}</dd></div>
          <div>
            <dt className="text-slate-500">Valid until</dt>
            <dd>{quotation.validUntil ? new Date(quotation.validUntil).toLocaleDateString() : '—'}</dd>
          </div>
          {quotation.opportunityId && (
            <div>
              <dt className="text-slate-500">Linked opportunity</dt>
              <dd>
                <Link to={RoutePath.EDIT_OPPORTUNITY.replace(':id', quotation.opportunityId)} className="text-indigo-600 underline">
                  View opportunity
                </Link>
              </dd>
            </div>
          )}
          <div className="sm:col-span-2"><dt className="text-slate-500">Notes</dt><dd>{quotation.notes || '—'}</dd></div>
        </dl>
      </div>
    </section>
  );
};

export default QuotationDetail;
