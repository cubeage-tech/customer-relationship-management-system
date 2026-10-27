import { useEffect, useState } from 'react';
import { getCampaignPrediction, predictCampaign } from '../../core/services/campaign.service';

const range = (e) => (e ? `${e.expected.toLocaleString('en-IN')} (${e.low.toLocaleString('en-IN')}–${e.high.toLocaleString('en-IN')})` : '—');

/**
 * FR-7.6: what the campaign is likely to achieve — before launch for the chosen segment, and once
 * launched next to what actually happened (predicted from the history before it).
 */
const CampaignForecast = ({ campaign, segmentId }) => {
  const launched = Boolean(campaign.launchedAt);
  const key = launched ? `c:${campaign.id}:${campaign.status}` : segmentId ? `s:${campaign.channel}:${segmentId}:${campaign.budget ?? ''}` : null;
  const [result, setResult] = useState({ key: null, data: null });

  useEffect(() => {
    if (!key) return undefined;
    let cancelled = false;
    const request = launched ? getCampaignPrediction(campaign.id)
      : predictCampaign({ channel: campaign.channel, segmentId, budget: campaign.budget || undefined });
    request.then((data) => !cancelled && setResult({ key, data })).catch(() => !cancelled && setResult({ key, data: null }));
    return () => {
      cancelled = true;
    };
  }, [key, launched, campaign.id, campaign.channel, campaign.budget, segmentId]);

  const p = result.key === key ? result.data : null;
  if (!p) return null;
  const rows = [
    ['Delivered', p.delivered, p.actual?.sent],
    ...(p.opened ? [['Opened', p.opened, p.actual?.opened], ['Clicked', p.clicked, p.actual?.clicked]] : []),
    ['Conversions', p.conversions, p.actual?.converted],
  ];

  return (
    <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
      <p className="font-semibold text-slate-900">{launched ? 'Predicted vs actual' : 'Predicted performance'}</p>
      <p className="text-xs text-slate-500">
        {p.reachable.toLocaleString('en-IN')} of {p.members.toLocaleString('en-IN')} customers reachable
        {p.basis === 'typical' ? ' · estimated from typical rates (little history yet)' : ' · from your past campaigns on this channel'}
      </p>
      <table className="mt-2 w-full text-left text-xs">
        <thead>
          <tr className="text-slate-500">
            <th className="py-1 font-medium" scope="col">Measure</th>
            <th className="py-1 font-medium" scope="col">Predicted (likely range)</th>
            {launched && <th className="py-1 font-medium" scope="col">Actual</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map(([label, estimate, actual]) => (
            <tr key={label} className="border-t border-slate-200">
              <td className="py-1 text-slate-700">{label}</td>
              <td className="py-1 font-medium text-slate-900">{range(estimate)}</td>
              {launched && <td className="py-1 text-slate-900">{actual ?? '—'}</td>}
            </tr>
          ))}
        </tbody>
      </table>
      {p.costPerConversion != null && (
        <p className="mt-2 text-xs text-slate-600">Predicted cost per conversion: ₹{Number(p.costPerConversion).toLocaleString('en-IN')}</p>
      )}
      {p.notes?.length > 0 && (
        <ul className="mt-2 list-disc space-y-0.5 pl-4 text-xs text-slate-500">
          {p.notes.map((n) => <li key={n}>{n}</li>)}
        </ul>
      )}
    </div>
  );
};

export default CampaignForecast;
