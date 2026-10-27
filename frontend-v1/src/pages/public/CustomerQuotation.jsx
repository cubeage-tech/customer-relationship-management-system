import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import Button from '../../components/common/Button';
import { QUOTATION_STATUS_LABELS, QUOTATION_STATUSES } from '../../core/constants/app.constant';
import { downloadCustomerQuotationPdf, getCustomerQuotation } from '../../core/services/quotation.service';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

const STATUS_NOTES = {
  [QUOTATION_STATUSES.EXPIRED]: 'This offer has expired. Please contact us for an updated quotation.',
  [QUOTATION_STATUSES.APPROVED]: 'You have approved this quotation. Thank you!',
  [QUOTATION_STATUSES.REJECTED]: 'This quotation was declined.',
};

/**
 * FR-4.5: the page a customer opens from the emailed link. No login — the token in the URL is the
 * credential. Opening it marks the quotation as viewed for the sales team.
 */
const CustomerQuotation = () => {
  const { token } = useParams();
  const [quotation, setQuotation] = useState(null);
  const [error, setError] = useState('');
  const [downloadError, setDownloadError] = useState('');

  useEffect(() => {
    getCustomerQuotation(token)
      .then(setQuotation)
      .catch((err) => setError(err.response?.status === 404
        ? 'This quotation link is not valid. Please check the link in your email.'
        : 'We could not load this quotation. Please try again later.'));
  }, [token]);

  const download = () => {
    setDownloadError('');
    downloadCustomerQuotationPdf(token, quotation.quotationNumber)
      .catch(() => setDownloadError('The PDF could not be downloaded. Please try again.'));
  };

  if (error) {
    return <main className="mx-auto max-w-2xl px-4 py-16 text-center text-slate-700">{error}</main>;
  }
  if (!quotation) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-16" role="status" aria-label="Loading quotation">
        <div className="h-40 animate-pulse rounded-lg bg-slate-100" />
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm text-slate-500">{quotation.companyName}</p>
            <h1 className="mt-1 text-xl font-bold text-slate-900">Quotation {quotation.quotationNumber}</h1>
            <p className="mt-1 text-sm text-slate-600">For {quotation.customerName}</p>
          </div>
          <div className="text-right text-sm">
            <span className="inline-flex rounded-full bg-primary/5 px-2.5 py-1 text-xs font-semibold text-primary">
              {QUOTATION_STATUS_LABELS[quotation.status] || quotation.status}
            </span>
            {quotation.validUntil && (
              <p className="mt-2 text-slate-500">Valid until {new Date(quotation.validUntil).toLocaleDateString()}</p>
            )}
          </div>
        </div>

        {STATUS_NOTES[quotation.status] && (
          <p className="mt-4 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-700">{STATUS_NOTES[quotation.status]}</p>
        )}

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
              <tr>
                <th className="py-2 pr-4 font-semibold">Product / service</th>
                <th className="py-2 pr-4 text-right font-semibold">Qty</th>
                <th className="py-2 pr-4 text-right font-semibold">Unit price</th>
                <th className="py-2 pr-4 text-right font-semibold">Discount</th>
                <th className="py-2 text-right font-semibold">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {quotation.lineItems.map((item) => (
                <tr key={item.id}>
                  <td className="py-2 pr-4 text-slate-800">{item.productName}</td>
                  <td className="py-2 pr-4 text-right">{item.quantity}</td>
                  <td className="py-2 pr-4 text-right">{formatCurrency(item.unitPrice)}</td>
                  <td className="py-2 pr-4 text-right">{item.discountPercent}%</td>
                  <td className="py-2 text-right font-medium">{formatCurrency(item.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-4 flex flex-col items-end gap-1 text-sm">
          <p className="text-slate-500">Subtotal {formatCurrency(quotation.subtotal)}</p>
          <p className="text-lg font-bold text-slate-900">Total {formatCurrency(quotation.grandTotal)}</p>
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4">
          <Button onClick={download}>Download PDF</Button>
          <p className="text-sm text-slate-500">To accept or ask a question, simply reply to the email you received.</p>
        </div>
        {downloadError && <p className="mt-2 text-sm text-red-600">{downloadError}</p>}
      </div>
    </main>
  );
};

export default CustomerQuotation;
