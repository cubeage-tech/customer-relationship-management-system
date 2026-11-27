import { useState } from 'react';
import ScoreBadge from '../ai/ScoreBadge';
import { Link } from 'react-router-dom';
import { CalendarClock, UserRound } from 'lucide-react';
import RoutePath from '../../core/constants/routes.constant';
import { LEAD_SOURCE_LABELS, LEAD_STAGE_OPTIONS } from '../../core/constants/app.constant';

/**
 * NAV-M03: kanban view of the lead pipeline, one column per stage. With edit rights, cards move
 * between stages by drag and drop or the card's stage select (skips are confirmed by the page).
 */
const LeadBoard = ({ leads, stages = LEAD_STAGE_OPTIONS, canEdit, onStageChange, isOverdue }) => {
  const [dragId, setDragId] = useState(null);
  const [overStage, setOverStage] = useState(null);

  const drop = (stage) => {
    const lead = leads.find((l) => l.id === dragId);
    setDragId(null);
    setOverStage(null);
    if (lead && lead.stage !== stage) onStageChange(lead, stage);
  };

  return (
    <div className="flex gap-3 overflow-x-auto p-4" aria-label="Lead board">
      {stages.map(({ value: stage, label }) => {
        const cards = leads.filter((l) => l.stage === stage);
        return (
          <section
            key={stage}
            aria-label={`${label} column`}
            className={`flex w-64 shrink-0 flex-col rounded-lg border bg-slate-50 ${overStage === stage ? 'border-primary/50 ring-2 ring-primary/20' : 'border-slate-200'}`}
            onDragOver={canEdit ? (e) => { e.preventDefault(); setOverStage(stage); } : undefined}
            onDragLeave={canEdit ? () => setOverStage((s) => (s === stage ? null : s)) : undefined}
            onDrop={canEdit ? (e) => { e.preventDefault(); drop(stage); } : undefined}
          >
            <header className="flex items-center justify-between gap-2 border-b border-slate-200 px-3 py-2.5">
              <h3 className="text-xs font-semibold uppercase text-slate-600">{label}</h3>
              <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-slate-600">{cards.length}</span>
            </header>
            <div className="flex min-h-24 flex-1 flex-col gap-2 p-2">
              {cards.length === 0 && <p className="px-1 py-3 text-center text-xs text-slate-400">No leads</p>}
              {cards.map((lead) => (
                <article
                  key={lead.id}
                  draggable={canEdit}
                  onDragStart={canEdit ? (e) => { setDragId(lead.id); e.dataTransfer.effectAllowed = 'move'; } : undefined}
                  onDragEnd={() => { setDragId(null); setOverStage(null); }}
                  className={`rounded-md border border-slate-200 bg-white p-3 shadow-sm ${canEdit ? 'cursor-grab active:cursor-grabbing' : ''} ${dragId === lead.id ? 'opacity-50' : ''}`}
                >
                  <Link to={RoutePath.EDIT_LEAD.replace(':id', lead.id)} className="text-sm font-semibold text-slate-800 hover:text-primary/80 hover:underline">
                    {lead.leadName}
                  </Link>
                  <p className="mt-0.5 truncate text-xs text-slate-500">{lead.companyName} · {LEAD_SOURCE_LABELS[lead.source] || lead.source}</p>
                  {lead.aiScore != null && <div className="mt-1.5"><ScoreBadge value={lead.aiScore} label={lead.aiScoreLabel} title="AI lead score" /></div>}
                  <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
                    {lead.followUpDate && (
                      <span className={`inline-flex items-center gap-1 ${isOverdue?.(lead) ? 'font-semibold text-rose-600' : ''}`}>
                        <CalendarClock size={12} aria-hidden="true" />{new Date(lead.followUpDate).toLocaleDateString()}
                      </span>
                    )}
                    <span className="inline-flex items-center gap-1"><UserRound size={12} aria-hidden="true" />{lead.ownerName || '—'}</span>
                  </div>
                  {canEdit && (
                    <select
                      value={lead.stage}
                      onChange={(e) => onStageChange(lead, e.target.value)}
                      aria-label={`Stage for ${lead.leadName}`}
                      className="mt-2 h-7 w-full rounded-md border border-slate-200 bg-white px-2 text-xs text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                    >
                      {LEAD_STAGE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
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

export default LeadBoard;
