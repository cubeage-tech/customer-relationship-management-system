import { useEffect, useState } from 'react';
import { getSegmentRecommendations } from '../../core/services/campaign.service';

const pct = (v) => `${Math.round((v ?? 0) * 1000) / 10}%`;

/**
 * FR-7.5: the segments most likely to respond on this channel, best first — predicted response rate,
 * reachable customers and why. Picking one fills in the segment.
 */
const SegmentSuggestions = ({ channel, value, onPick }) => {
  const [recs, setRecs] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getSegmentRecommendations(channel)
      .then((data) => !cancelled && setRecs(data ?? []))
      .catch(() => !cancelled && setRecs([]));
    return () => {
      cancelled = true;
    };
  }, [channel]);

  const useful = (recs ?? []).filter((r) => r.reachable > 0).slice(0, 3);
  if (useful.length === 0) return null;
  const measure = useful[0].responseMeasure === 'open' ? 'open' : 'convert';

  return (
    <div className="rounded-md border border-indigo-100 bg-indigo-50/60 p-3 text-xs text-indigo-900">
      <p className="font-semibold">Most likely to respond</p>
      <ul className="mt-1 space-y-1.5">
        {useful.map((r) => (
          <li key={r.segmentId} className="flex flex-wrap items-start justify-between gap-2">
            <span className="min-w-0 flex-1">
              <span className="font-medium">{r.name}</span> — {pct(r.predictedResponseRate)} likely to {measure} · {r.reachable} reachable
              <span className="block text-indigo-700">{r.reason}</span>
            </span>
            {String(value) !== String(r.segmentId) && (
              <button type="button" onClick={() => onPick(r.segmentId)} className="rounded border border-indigo-200 bg-white px-2 py-0.5 font-medium text-indigo-700 hover:bg-indigo-100">
                Use
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default SegmentSuggestions;
