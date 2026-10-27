import { Sparkles } from 'lucide-react';

const money = (v) => `₹${Number(v ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

/**
 * FR-4.7: under a quotation line — the discount past quotes for this product were most often won at
 * (for this customer when they have history), with the reason, and a button to use it.
 */
const PriceHint = ({ rec, currentDiscount, onApply }) => {
  if (!rec || rec.confidence === 'none') return null;
  const discount = Number(rec.recommendedDiscountPercent);
  const applied = Number(currentDiscount || 0) === discount;
  return (
    <div className="col-span-full flex flex-wrap items-start gap-2 rounded-md bg-indigo-50 px-3 py-2 text-xs text-indigo-900">
      <Sparkles size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
      <p className="min-w-0 flex-1">
        <span className="font-semibold">Suggested: {discount > 0 ? `${discount}% off` : 'list price'}</span>
        {rec.recommendedNetPrice != null && <> ({money(rec.recommendedNetPrice)} each)</>}
        {' · '}{rec.confidence} confidence, {rec.decidedQuotes} past quote{rec.decidedQuotes === 1 ? '' : 's'}
        {rec.needsApproval && ' · above your approval threshold'}
        <span className="block text-indigo-700">{rec.explanation}</span>
      </p>
      {!applied && (
        <button type="button" onClick={() => onApply(discount)} className="rounded border border-indigo-200 bg-white px-2 py-1 font-medium text-indigo-700 hover:bg-indigo-100">
          Use {discount}%
        </button>
      )}
    </div>
  );
};

export default PriceHint;
