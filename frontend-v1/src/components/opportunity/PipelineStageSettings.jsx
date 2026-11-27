import { useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { usePipelineStages } from '../../core/hooks/usePipelineStages';
import { createPipelineStage, reorderPipelineStages, updatePipelineStage } from '../../core/services/pipelineStage.service';

/**
 * FR-3.2: tenant admin maintains the sales stages. Open stages can be added, renamed, reordered
 * and deactivated; Won and Lost are fixed end stages that can only be renamed. Stages are never
 * deleted, so existing opportunities keep theirs.
 */
const PipelineStageSettings = () => {
  const { stages, reload } = usePipelineStages();
  const [newLabel, setNewLabel] = useState('');
  const [editing, setEditing] = useState(null); // { code, label }
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const openStages = stages.filter((s) => !s.terminal);

  const run = async (action, fallback) => {
    setBusy(true);
    setError('');
    try {
      await action();
      await reload();
      return true;
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const add = async (e) => {
    e.preventDefault();
    if (await run(() => createPipelineStage({ label: newLabel }), 'Could not add this stage.')) setNewLabel('');
  };

  const rename = async (e) => {
    e.preventDefault();
    if (await run(() => updatePipelineStage(editing.code, { label: editing.label }), 'Could not rename this stage.')) setEditing(null);
  };

  const move = (code, offset) => {
    const codes = openStages.map((s) => s.code);
    const from = codes.indexOf(code);
    const to = from + offset;
    if (to < 0 || to >= codes.length) return;
    [codes[from], codes[to]] = [codes[to], codes[from]];
    run(() => reorderPipelineStages(codes), 'Could not reorder the stages.');
  };

  const linkClass = 'text-blue-600 hover:underline disabled:opacity-40 disabled:no-underline';

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Sales stages</h2>
      <p className="text-sm text-slate-500 mt-1">
        The pipeline opportunities move through. New deals start in the first active stage. Won and Lost always close a deal.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      <div className="mt-3 divide-y divide-slate-100">
        {stages.map((stage) => {
          const index = openStages.findIndex((s) => s.code === stage.code);
          return (
            <div key={stage.code} className="flex flex-wrap items-center justify-between gap-3 py-2">
              {editing?.code === stage.code ? (
                <form onSubmit={rename} className="flex flex-wrap items-center gap-2">
                  <input value={editing.label} onChange={(e) => setEditing({ ...editing, label: e.target.value })} maxLength={60}
                    className="border border-slate-300 rounded px-2 py-1 text-sm" aria-label="Stage name" required />
                  <Button type="submit" disabled={busy}>Save</Button>
                  <button type="button" className="text-sm text-slate-500 hover:underline" onClick={() => setEditing(null)}>Cancel</button>
                </form>
              ) : (
                <span className={`text-sm ${stage.active ? 'text-slate-800' : 'text-slate-400 line-through'}`}>
                  {stage.label}
                  {stage.terminal && <span className="ml-2 text-xs text-slate-400">closes the deal</span>}
                </span>
              )}
              {editing?.code !== stage.code && (
                <div className="flex gap-3 text-sm">
                  {!stage.terminal && (
                    <>
                      <button type="button" className={linkClass} disabled={busy || index === 0} onClick={() => move(stage.code, -1)} aria-label={`Move ${stage.label} up`}>Up</button>
                      <button type="button" className={linkClass} disabled={busy || index === openStages.length - 1} onClick={() => move(stage.code, 1)} aria-label={`Move ${stage.label} down`}>Down</button>
                    </>
                  )}
                  <button type="button" className={linkClass} onClick={() => setEditing({ code: stage.code, label: stage.label })}>Rename</button>
                  {!stage.terminal && (
                    <button type="button" className={linkClass} disabled={busy}
                      onClick={() => run(() => updatePipelineStage(stage.code, { active: !stage.active }), 'Could not update this stage.')}>
                      {stage.active ? 'Deactivate' : 'Activate'}
                    </button>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
      <form onSubmit={add} className="flex flex-wrap items-center gap-2 mt-3">
        <input value={newLabel} onChange={(e) => setNewLabel(e.target.value)} placeholder="New stage, e.g. Site Visit" maxLength={60}
          className="border border-slate-300 rounded px-2 py-1 text-sm" aria-label="New stage name" required />
        <Button type="submit" disabled={busy || !newLabel.trim()}>Add stage</Button>
      </form>
    </div>
  );
};

export default PipelineStageSettings;
