import { useCallback, useEffect, useState } from 'react';
import { listPipelineStages } from '../services/pipelineStage.service';
import { OPPORTUNITY_STAGE_LABELS, OPPORTUNITY_STAGE_OPTIONS } from '../constants/app.constant';

// Shown until the tenant's list has loaded (the SRS defaults every tenant starts with).
const FALLBACK = OPPORTUNITY_STAGE_OPTIONS.map((o, i) => ({
  code: o.value, label: o.label, sortOrder: i, active: true, terminal: o.value === 'won' || o.value === 'lost',
}));

/**
 * FR-3.2: the tenant's configurable sales stages. `options` are the active stages for pickers;
 * `optionsFor(current)` keeps a record's (possibly deactivated) current stage selectable;
 * `labelOf(code)` names any stage.
 */
export const usePipelineStages = () => {
  const [stages, setStages] = useState(FALLBACK);

  const reload = useCallback(
    () => listPipelineStages(true).then((data) => setStages(data?.length ? data : FALLBACK)).catch(() => setStages(FALLBACK)),
    [],
  );

  useEffect(() => {
    reload();
  }, [reload]);

  const labels = Object.fromEntries(stages.map((s) => [s.code, s.label]));
  const labelOf = (code) => labels[code] || OPPORTUNITY_STAGE_LABELS[code] || code;
  const options = stages.filter((s) => s.active).map((s) => ({ value: s.code, label: s.label }));
  const optionsFor = (current) =>
    !current || options.some((o) => o.value === current) ? options : [...options, { value: current, label: `${labelOf(current)} (inactive)` }];

  return { stages, options, labels, labelOf, optionsFor, reload };
};
