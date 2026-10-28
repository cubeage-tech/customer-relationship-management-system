import { useEffect, useState } from 'react';
import { getDealProbabilities } from '../services/ai.service';

/**
 * FR-AI-2.2: the close chance of every open deal the user can see, by deal id — for the deal list and
 * board. Empty for roles without forecast access. {@code reloadKey} refreshes it (e.g. after a move).
 */
export const useDealProbabilities = (reloadKey) => {
  const [byId, setById] = useState({});

  useEffect(() => {
    let cancelled = false;
    getDealProbabilities()
      .then((list) => {
        if (cancelled) return;
        const map = {};
        (list ?? []).forEach((d) => { map[d.opportunityId] = d; });
        setById(map);
      })
      .catch(() => !cancelled && setById({}));
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  return byId;
};
