import { useEffect, useState } from 'react';
import { Gift } from 'lucide-react';
import { getOfferRecommendations } from '../../core/services/campaign.service';

/**
 * FR-7.7: on the customer page — the current offers that suit this customer best, with the reasons.
 * Hidden for roles that can't see promotions.
 */
const OfferSuggestions = ({ customerId }) => {
  const [data, setData] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getOfferRecommendations(customerId).then((d) => !cancelled && setData(d)).catch(() => !cancelled && setData(null));
    return () => {
      cancelled = true;
    };
  }, [customerId]);

  if (!data || data.offers.length === 0) return null;

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Gift size={17} aria-hidden="true" /> Recommended offers</h2>
      {data.marketingOptOut && (
        <p className="mt-1 text-xs text-amber-700">This customer has unsubscribed from marketing — mention an offer in person rather than sending it.</p>
      )}
      <ul className="mt-3 divide-y divide-slate-100">
        {data.offers.map((o) => (
          <li key={o.promotionId} className="py-2 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium text-slate-900">{o.name} <span className="font-mono text-xs text-slate-500">{o.code}</span></span>
              <span className="text-slate-700">{o.discountLabel}{o.validUntil ? ` · until ${o.validUntil}` : ''} · fit {o.score}/100</span>
            </div>
            {o.reasons.length > 0 && <p className="mt-0.5 text-xs text-slate-500">{o.reasons.join(' · ')}</p>}
          </li>
        ))}
      </ul>
    </section>
  );
};

export default OfferSuggestions;
