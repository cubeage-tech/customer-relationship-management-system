import { useEffect, useState } from 'react';
import { Clock } from 'lucide-react';
import { getContactTime } from '../../core/services/ai.service';

/**
 * FR-AI-3.2: on a customer or lead page — the best weekday/time windows to get in touch, from when
 * they have actually engaged (or similar customers / business hours when there's little history).
 */
const ContactTimeCard = ({ customerId, leadId }) => {
  const key = `${customerId ?? ''}:${leadId ?? ''}`;
  const [result, setResult] = useState({ key: null, data: null });

  useEffect(() => {
    let cancelled = false;
    getContactTime({ customerId, leadId })
      .then((data) => !cancelled && setResult({ key, data }))
      .catch(() => !cancelled && setResult({ key, data: null }));
    return () => {
      cancelled = true;
    };
  }, [customerId, leadId, key]);

  const t = result.key === key ? result.data : null;
  if (!t || t.windows.length === 0) return null;

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Clock size={17} aria-hidden="true" /> Best time to contact</h2>
      <ul className="mt-2 flex flex-wrap gap-2">
        {t.windows.map((w) => (
          <li key={w.label} className="rounded-full bg-emerald-50 px-3 py-1 text-sm font-medium text-emerald-800">{w.label}</li>
        ))}
      </ul>
      <p className="mt-2 text-xs text-slate-500">Based on {t.basis}. {t.notes.join(' ')}</p>
    </section>
  );
};

export default ContactTimeCard;
