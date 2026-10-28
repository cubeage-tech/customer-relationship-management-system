import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import RoutePath from '../../core/constants/routes.constant';
import { getChurnRisks } from '../../core/services/ai.service';
import { RISK_STYLE } from './churnRiskStyle';

const SHOWN = 5;

/**
 * FR-AI-6.2: on the customer list — the customers you can see at medium or high churn risk, riskiest
 * first, with the main reason. Hidden when there are none or the role can't see them.
 */
const AtRiskCustomers = () => {
  const [risks, setRisks] = useState([]);
  const [all, setAll] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getChurnRisks('medium').then((d) => !cancelled && setRisks(d ?? [])).catch(() => !cancelled && setRisks([]));
    return () => {
      cancelled = true;
    };
  }, []);

  if (risks.length === 0) return null;
  const high = risks.filter((r) => r.level === 'high').length;

  return (
    <section className="mb-4 rounded-lg border border-red-100 bg-white p-4">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
        <ShieldAlert size={16} className="text-red-600" aria-hidden="true" />
        At risk of churning: {high} high, {risks.length - high} medium
      </h2>
      <ul className="mt-2 divide-y divide-slate-100 text-sm">
        {(all ? risks : risks.slice(0, SHOWN)).map((r) => (
          <li key={r.customerId} className="flex flex-wrap items-center justify-between gap-2 py-1.5">
            <span className="min-w-0">
              <Link to={RoutePath.EDIT_CUSTOMER.replace(':id', r.customerId)} className="font-medium text-primary hover:underline">{r.customerName}</Link>
              {r.factors[0] && <span className="text-slate-500"> — {r.factors[0].detail}</span>}
            </span>
            <span className="flex items-center gap-2 text-xs text-slate-500">
              {r.ownerName || 'No owner'}
              <span className={`rounded-full px-2 py-0.5 font-medium ${RISK_STYLE[r.level]}`}>{r.score}/100</span>
            </span>
          </li>
        ))}
      </ul>
      {risks.length > SHOWN && (
        <button type="button" onClick={() => setAll((v) => !v)} className="mt-1 text-xs text-primary hover:underline">
          {all ? 'Show fewer' : `Show all ${risks.length}`}
        </button>
      )}
    </section>
  );
};

export default AtRiskCustomers;
