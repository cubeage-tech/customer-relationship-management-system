import { useCallback, useEffect, useState } from 'react';
import { listIndustries } from '../services/industry.service';

/**
 * FR-1.6: the tenant's configurable customer industries. `options` are the active ones for pickers;
 * `optionsFor(current)` also keeps a record's (possibly deactivated) current value selectable;
 * `labelOf(code)` names any code, active or not.
 */
export const useIndustries = () => {
  const [industries, setIndustries] = useState([]);

  const reload = useCallback(
    () => listIndustries(true).then((data) => setIndustries(data ?? [])).catch(() => setIndustries([])),
    [],
  );

  useEffect(() => {
    reload();
  }, [reload]);

  const options = industries.filter((i) => i.active).map((i) => ({ value: i.code, label: i.label }));
  const labels = Object.fromEntries(industries.map((i) => [i.code, i.label]));
  const labelOf = (code) => labels[code] || code;
  const optionsFor = (current) =>
    !current || options.some((o) => o.value === current) ? options : [...options, { value: current, label: `${labelOf(current)} (inactive)` }];

  return { industries, options, labels, labelOf, optionsFor, reload };
};
