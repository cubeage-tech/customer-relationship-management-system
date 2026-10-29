import { useEffect, useState } from 'react';
import { PackagePlus } from 'lucide-react';
import { getProductRecommendations } from '../../core/services/ai.service';
import { formatMoney } from '../../core/utils/reportPeriod';

const KINDS = { bought_together: 'Bought together', next_purchase: 'Often bought next', reorder: 'Due for re-order',
  popular_in_industry: 'Popular in their industry' };

/**
 * FR-AI-3.1: on the customer page — products to offer next from purchase history (bought together,
 * bought next, re-orders due), each with the reason. Hidden when there is nothing to suggest.
 */
const ProductSuggestions = ({ customerId }) => {
  const [data, setData] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getProductRecommendations(customerId).then((d) => !cancelled && setData(d)).catch(() => !cancelled && setData(null));
    return () => {
      cancelled = true;
    };
  }, [customerId]);

  if (!data || data.recommendations.length === 0) return null;

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900"><PackagePlus size={17} aria-hidden="true" /> Suggested products</h2>
      <ul className="mt-3 divide-y divide-slate-100">
        {data.recommendations.map((r) => (
          <li key={r.productName} className="py-2 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium text-slate-900">{r.productName}</span>
              <span className="text-xs text-slate-600">
                <span className="rounded-full bg-indigo-50 px-2 py-0.5 font-medium text-indigo-700">{KINDS[r.kind] || r.kind}</span>
                {r.unitPrice != null && <> · {formatMoney(r.unitPrice)}</>}
              </span>
            </div>
            <p className="mt-0.5 text-xs text-slate-500">{r.reason}</p>
          </li>
        ))}
      </ul>
    </section>
  );
};

export default ProductSuggestions;
