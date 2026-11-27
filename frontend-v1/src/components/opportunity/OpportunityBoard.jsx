import { useState } from 'react';
import ScoreBadge from '../ai/ScoreBadge';
import { Link } from 'react-router-dom';
import { CalendarDays, UserRound } from 'lucide-react';
import RoutePath from '../../core/constants/routes.constant';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

/**
 * FR-3.3: kanban view of the pipeline — one column per stage with its deal count and cumulative
 * value. With edit rights, cards move between stages by drag and drop or the card's stage select.
 * FR-3.2: `stages` are the tenant's columns; `stageOptionsFor(stage)` the card select's choices.
 */
const OpportunityBoard = ({ opportunities, chances = {}, stages, stageOptionsFor, canEdit, onStageChange }) => {
  const [dragId, setDragId] = useState(null);
  const [overStage, setOverStage] = useState(null);

  const drop = (stage) => {
    const opportunity = opportunities.find((o) => o.id === dragId);
    setDragId(null);
    setOverStage(null);
    if (opportunity && opportunity.stage !== stage) onStageChange(opportunity, stage);
  };

  return (
    <div className="flex gap-3 overflow-x-auto p-4" aria-label="Opportunity board">
      {stages.map(({ value: stage, label }) => {
        const cards = opportunities.filter((o) => o.stage === stage);
        const total = cards.reduce((sum, o) => sum + Number(o.dealValue ?? 0), 0);
        return (
          <section
            key={stage}
            aria-label={`${label} column`}
            className={`flex w-72 shrink-0 flex-col rounded-lg border bg-slate-50 ${overStage === stage ? 'border-primary/50 ring-2 ring-primary/20' : 'border-slate-200'}`}
            onDragOver={canEdit ? (e) => { e.preventDefault(); setOverStage(stage); } : undefined}
            onDragLeave={canEdit ? () => setOverStage((s) => (s === stage ? null : s)) : undefined}
            onDrop={canEdit ? (e) => { e.preventDefault(); drop(stage); } : undefined}
          >
            <header className="border-b border-slate-200 px-3 py-2.5">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-xs font-semibold uppercase text-slate-600">{label}</h3>
                <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-slate-600">{cards.length}</span>
              </div>
              <p className="mt-1 text-sm font-bold text-slate-900">{formatCurrency(total)}</p>
            </header>
            <div className="flex min-h-24 flex-1 flex-col gap-2 p-2">
              {cards.length === 0 && <p className="px-1 py-3 text-center text-xs text-slate-400">No deals</p>}
              {cards.map((o) => (
                <article
                  key={o.id}
                  draggable={canEdit}
                  onDragStart={canEdit ? (e) => { setDragId(o.id); e.dataTransfer.effectAllowed = 'move'; } : undefined}
                  onDragEnd={() => { setDragId(null); setOverStage(null); }}
                  className={`rounded-md border border-slate-200 bg-white p-3 shadow-sm ${canEdit ? 'cursor-grab active:cursor-grabbing' : ''} ${dragId === o.id ? 'opacity-50' : ''}`}
                >
                  <Link to={RoutePath.EDIT_OPPORTUNITY.replace(':id', o.id)} className="text-sm font-semibold text-slate-800 hover:text-primary/80 hover:underline">
                    {o.customerName}
                  </Link>
                  {o.productService && <p className="mt-0.5 truncate text-xs text-slate-500">{o.productService}</p>}
                  <div className="mt-2 flex items-center justify-between gap-2">
                    <p className="text-sm font-bold text-slate-900">{formatCurrency(o.dealValue)}</p>
                    {chances[o.id] && <ScoreBadge value={chances[o.id].percent} suffix="%" label={chances[o.id].label} title="Chance to win" />}
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
                    {o.expectedClosingDate && (
                      <span className="inline-flex items-center gap-1"><CalendarDays size={12} aria-hidden="true" />{new Date(o.expectedClosingDate).toLocaleDateString()}</span>
                    )}
                    <span className="inline-flex items-center gap-1"><UserRound size={12} aria-hidden="true" />{o.ownerName || '—'}</span>
                  </div>
                  {canEdit && (
                    <select
                      value={o.stage}
                      onChange={(e) => onStageChange(o, e.target.value)}
                      aria-label={`Stage for ${o.customerName}`}
                      className="mt-2 h-7 w-full rounded-md border border-slate-200 bg-white px-2 text-xs text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                    >
                      {stageOptionsFor(o.stage).map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                    </select>
                  )}
                </article>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
};

export default OpportunityBoard;
