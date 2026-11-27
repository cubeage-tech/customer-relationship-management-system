import { useEffect, useState } from 'react';
import { getAiPerformance } from '../../core/services/ai.service';

const LABELS = {
  lead_score: 'Lead scores', forecast: 'Revenue forecast', price_recommendation: 'Price recommendations',
  segment_recommendation: 'Segment suggestions', campaign_prediction: 'Campaign predictions', offer_recommendation: 'Offer suggestions',
  quotation_draft: 'Quotation drafts', assistant_chat: 'Assistant', message_writing: 'Message writing', sentiment: 'Sentiment',
};

/**
 * NFR-7.1c: how fast each AI feature has answered on this server — the typical (p50) and slow (p95)
 * times against the budget: 2 s for statistical features, 8 s for generative ones.
 */
const AiPerformance = () => {
  const [rows, setRows] = useState(null);

  useEffect(() => {
    getAiPerformance().then((d) => setRows(d ?? [])).catch(() => setRows([]));
  }, []);

  if (!rows) return null;

  return (
    <div className="mt-4">
      <h3 className="text-sm font-semibold text-slate-900">AI response times</h3>
      <p className="text-xs text-slate-500">Since this server started. Budget: 2 s for statistical features, 8 s for generative ones.</p>
      {rows.length === 0 ? (
        <p className="mt-2 text-sm text-slate-400">No AI feature has been used yet.</p>
      ) : (
        <table className="mt-2 w-full text-left text-xs">
          <thead>
            <tr className="text-slate-500">
              <th className="py-1 font-medium" scope="col">Feature</th>
              <th className="py-1 font-medium" scope="col">Typical</th>
              <th className="py-1 font-medium" scope="col">Slowest 5%</th>
              <th className="py-1 font-medium" scope="col">Within budget</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.feature} className="border-t border-slate-100">
                <td className="py-1 text-slate-700">{LABELS[r.feature] || r.feature} <span className="text-slate-400">({r.samples})</span></td>
                <td className="py-1">{(r.p50Ms / 1000).toFixed(2)} s</td>
                <td className={`py-1 ${r.p95Ms > r.budgetMs ? 'text-red-700' : 'text-slate-900'}`}>{(r.p95Ms / 1000).toFixed(2)} s</td>
                <td className="py-1">{r.withinBudgetPercent}% of {r.budgetMs / 1000} s</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default AiPerformance;
