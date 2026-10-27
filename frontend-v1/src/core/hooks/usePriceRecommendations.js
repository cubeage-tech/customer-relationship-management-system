import { useEffect, useState } from 'react';
import { getPriceRecommendations } from '../services/ai.service';

/**
 * FR-4.7: the recommended discount for each product on a quotation, for its customer, from past
 * quotes won and lost. Returns { [lower-case product name]: recommendation }.
 */
export const usePriceRecommendations = (customerId, productNames) => {
  const names = [...new Set(productNames.map((n) => (n || '').trim()).filter(Boolean))].sort();
  const key = customerId && names.length > 0 ? JSON.stringify([Number(customerId), names]) : null;
  const [result, setResult] = useState({ key: null, byName: {} });

  useEffect(() => {
    if (!key) return undefined;
    const [customer, productList] = JSON.parse(key);
    let cancelled = false;
    const timer = window.setTimeout(() => {
      getPriceRecommendations({ customerId: customer, productNames: productList })
        .then((recs) => {
          if (cancelled) return;
          const byName = {};
          (recs ?? []).forEach((r) => { byName[r.productName.toLowerCase()] = r; });
          setResult({ key, byName });
        })
        .catch(() => !cancelled && setResult({ key, byName: {} }));
    }, 600);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [key]);

  return result.key === key ? result.byName : {};
};
