import { useState } from 'react';
import { FileText } from 'lucide-react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { draftProposal } from '../../core/services/ai.service';

const TONES = [
  { value: 'friendly', label: 'Friendly' },
  { value: 'formal', label: 'Formal' },
  { value: 'persuasive', label: 'Persuasive' },
];

/**
 * FR-AI-5.3: on a deal page — AI-written proposal text from the deal and its latest quotation, in an
 * editable box to review and copy into the proposal or email. Nothing is saved or sent from here.
 */
const ProposalCard = ({ opportunityId }) => {
  const [tone, setTone] = useState('formal');
  const [notes, setNotes] = useState('');
  const [data, setData] = useState(null);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const [showFacts, setShowFacts] = useState(false);

  const draft = async () => {
    setBusy(true);
    setError('');
    setCopied(false);
    try {
      const proposal = await draftProposal({ opportunityId, tone, notes: notes.trim() || undefined });
      setData(proposal);
      setText(proposal.text);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not draft the proposal.'));
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setError('Could not copy — select the text and copy it instead.');
    }
  };

  return (
    <section className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><FileText size={17} aria-hidden="true" /> Proposal text</h2>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <label className="sr-only" htmlFor="proposal-tone">Tone</label>
          <select id="proposal-tone" value={tone} onChange={(e) => setTone(e.target.value)} className="rounded border border-slate-300 px-2 py-1">
            {TONES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
          <Button size="sm" variant="outline" onClick={draft} disabled={busy}>
            {busy ? 'Writing…' : data ? 'Write again' : 'Write proposal with AI'}
          </Button>
        </div>
      </div>
      <input value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} placeholder="Points to include (optional), e.g. installation in March"
        className="mt-3 w-full rounded border border-slate-300 px-2 py-1 text-sm" aria-label="Points to include" />
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {!data && !error && <p className="mt-1 text-xs text-slate-500">Drafted from this deal and its latest quotation.</p>}
      {data && (
        <div className="mt-3 space-y-2 text-sm">
          <div className="rounded-md border border-amber-200 bg-amber-50 p-2 text-xs text-amber-900">
            Written by AI{data.quotationNumber ? ` from quotation ${data.quotationNumber}` : ' (no quotation yet — prices are not included)'} — check every figure and promise, then edit before you use it.
          </div>
          <textarea value={text} onChange={(e) => { setText(e.target.value); setCopied(false); }} rows={14}
            className="w-full rounded border border-slate-300 px-2 py-1 font-mono text-xs leading-relaxed" aria-label="Proposal text" />
          <div className="flex flex-wrap items-center gap-3">
            <Button size="sm" onClick={copy} disabled={!text.trim()}>{copied ? 'Copied' : 'Copy text'}</Button>
            <button type="button" onClick={() => setShowFacts((v) => !v)} className="text-xs text-primary hover:underline">
              {showFacts ? 'Hide' : 'Show'} what this is based on
            </button>
          </div>
          {showFacts && <ul className="list-disc pl-5 text-xs text-slate-500">{data.basedOn.map((f) => <li key={f}>{f}</li>)}</ul>}
        </div>
      )}
    </section>
  );
};

export default ProposalCard;
