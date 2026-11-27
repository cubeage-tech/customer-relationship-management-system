import { useEffect, useState } from 'react';
import StatCard from '../common/StatCard';
import BarList from '../analytics/BarList';
import { apiErrorMessage } from '../../core/utils/apiError';
import { formatMoney } from '../../core/utils/reportPeriod';
import { getCampaignPerformance } from '../../core/services/admin.service';

const pct = (v) => (v == null ? '—' : `${v}%`);

/**
 * API-G08 / NAV-M08: the campaign's performance report — delivery and engagement, the leads it
 * generated, the revenue won from them, cost per lead and return on spend, and activity by day.
 */
const CampaignPerformance = ({ campaignId, reloadKey }) => {
  const [result, setResult] = useState({ key: null, data: null, error: '' });
  const key = `${campaignId}:${reloadKey ?? ''}`;

  useEffect(() => {
    const k = `${campaignId}:${reloadKey ?? ''}`;
    getCampaignPerformance(campaignId)
      .then((data) => setResult({ key: k, data, error: '' }))
      .catch((err) => setResult({ key: k, data: null, error: apiErrorMessage(err, 'Could not load the performance report.') }));
  }, [campaignId, reloadKey]);

  if (result.key !== key) return null;
  if (result.error) return <p className="text-sm text-red-600 mb-6">{result.error}</p>;
  const p = result.data;
  const m = p.metrics;

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
      <h2 className="font-semibold text-slate-900">Performance</h2>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 mt-3">
        <StatCard label="Sent" value={m.sent} hint={`${m.failed} failed · ${m.skipped} skipped`} />
        <StatCard label="Opened" value={pct(m.openRate)} hint={`${m.opened} people · clicked ${pct(m.clickRate)}`} />
        <StatCard label="Leads generated" value={m.leadsGenerated} hint={`${m.convertedLeads} converted`} />
        <StatCard label="Revenue won" value={formatMoney(p.attributedRevenue)} hint={`${p.wonDeals} deal(s) from its leads`} />
        <StatCard label="Cost" value={formatMoney(p.actualCost)} hint={p.budget ? `budget ${formatMoney(p.budget)}` : 'no budget set'} />
        <StatCard label="Cost per lead" value={p.costPerLead == null ? '—' : formatMoney(p.costPerLead)}
          hint={p.costPerConversion == null ? '' : `${formatMoney(p.costPerConversion)} per conversion`} />
        <StatCard label="Return on spend" value={p.returnOnSpendPercent == null ? '—' : `${p.returnOnSpendPercent}%`}
          hint="revenue won minus cost, over cost" />
        <StatCard label="Converted" value={m.converted} hint={`${pct(m.conversionRate)} of sent`} />
      </div>
      {p.daily.length > 0 && (
        <div className="mt-4">
          <BarList title="Sent by day" rows={p.daily.map((d) => ({ key: d.date, label: new Date(`${d.date}T00:00:00`).toLocaleDateString(),
            count: d.sent, value: d.sent }))} measure="count" />
        </div>
      )}
    </div>
  );
};

export default CampaignPerformance;
