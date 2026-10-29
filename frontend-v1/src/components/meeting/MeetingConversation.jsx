import { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import Button from '../common/Button';
import MeetingActionItems from './MeetingActionItems';
import { apiErrorMessage } from '../../core/utils/apiError';
import { deleteMeetingTranscript, getMeetingConversation, saveMeetingTranscript, summarizeMeeting } from '../../core/services/meeting.service';

const METHODS = [
  { value: 'verbal', label: 'Said yes at the start (verbal)' },
  { value: 'written', label: 'In writing' },
  { value: 'email', label: 'By email' },
];
const MOOD = { positive: 'text-emerald-700', neutral: 'text-slate-600', negative: 'text-red-700', mixed: 'text-amber-700' };
const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

const List = ({ title, items }) => (items?.length ? (
  <div>
    <h4 className="text-xs font-semibold uppercase text-slate-500">{title}</h4>
    <ul className="mt-0.5 list-disc space-y-0.5 pl-5 text-slate-700">{items.map((t) => <li key={t}>{t}</li>)}</ul>
  </div>
) : null);

/**
 * FR-AI-7.2: the meeting's conversation — its transcript (captured while recording, or pasted /
 * uploaded with consent) — and the AI's summary: overview, key points, decisions, concerns, how the
 * customer came across and next steps. {@code version} changes when a recording saved a transcript.
 */
const MeetingConversation = ({ meeting, canEdit, version }) => {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [adding, setAdding] = useState(false);
  const [showText, setShowText] = useState(false);
  const [form, setForm] = useState({ text: '', source: 'pasted', givenBy: '', method: 'verbal', confirmed: false });

  const load = () => getMeetingConversation(meeting.id).then(setData).catch((err) => setError(apiErrorMessage(err, 'Could not load the conversation.')));

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meeting.id, version]);

  const run = async (action, fallback) => {
    setBusy(true);
    setError('');
    try {
      await action();
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  };

  const readFile = (file) => {
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      setError('That file is too large for a transcript.');
      return;
    }
    file.text().then((text) => setForm((f) => ({ ...f, text, source: 'upload' })));
  };

  const save = (e) => {
    e.preventDefault();
    run(async () => {
      await saveMeetingTranscript(meeting.id, {
        text: form.text, source: form.source, consentConfirmed: form.confirmed, consentGivenBy: form.givenBy.trim(), consentMethod: form.method,
      });
      setAdding(false);
      setForm({ text: '', source: 'pasted', givenBy: '', method: 'verbal', confirmed: false });
    }, 'Could not save the transcript.');
  };

  const remove = () => {
    const reason = window.prompt('Delete the transcript and its summary? Reason (optional):', '');
    if (reason !== null) run(() => deleteMeetingTranscript(meeting.id, reason), 'Could not delete the transcript.');
  };

  if (!data) return error ? <p className="mt-2 text-sm text-red-600">{error}</p> : null;
  const s = data.summary;

  return (
    <div className="mt-2 w-full rounded-md bg-slate-50 p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-medium text-slate-800">Conversation and summary</h3>
        {data.transcript && canEdit && (
          <Button size="sm" variant="outline" icon={Sparkles} disabled={busy} onClick={() => run(() => summarizeMeeting(meeting.id), 'Could not summarise the meeting.')}>
            {busy ? 'Working…' : s ? 'Summarise again' : 'Summarise with AI'}
          </Button>
        )}
      </div>
      {error && <p className="mt-1 text-red-600">{error}</p>}

      {s && (
        <div className="mt-2 space-y-2 rounded border border-slate-200 bg-white p-3">
          {data.summaryOutdated && <p className="text-xs text-amber-700">The transcript changed after this summary — summarise again.</p>}
          <p className="text-slate-800">{s.overview}</p>
          <p className="text-xs">Customer came across: <span className={`font-medium ${MOOD[s.customerSentiment] || ''}`}>{s.customerSentiment}</span></p>
          <List title="Key points" items={s.keyPoints} />
          <List title="Decisions" items={s.decisions} />
          <List title="Concerns raised" items={s.concerns} />
          <List title="Next steps" items={s.nextSteps} />
          <p className="text-xs text-slate-400">
            AI summary by {s.generatedByName}, {new Date(s.generatedAt).toLocaleString()}{s.partial ? ' · from the start and end of a long conversation' : ''} — check it against the conversation.
          </p>
        </div>
      )}

      {data.transcript ? (
        <div className="mt-2">
          <p className="text-xs text-slate-500">
            Transcript ({data.words} words, {data.source === 'browser' ? 'live captions while recording' : data.source === 'upload' ? 'uploaded' : 'pasted'})
            · consent {data.consentMethod} by {data.consentGivenBy} · {data.transcriptByName}, {new Date(data.transcriptAt).toLocaleString()}
          </p>
          <div className="mt-1 flex gap-3 text-xs">
            <button type="button" className="text-blue-600 hover:underline" onClick={() => setShowText((v) => !v)}>{showText ? 'Hide' : 'Show'} transcript</button>
            {canEdit && <button type="button" className="text-blue-600 hover:underline" onClick={() => setAdding(true)}>Replace</button>}
            {canEdit && <button type="button" className="text-red-600 hover:underline" onClick={remove}>Delete</button>}
          </div>
          {/* FR-AI-7.3 */}
          {canEdit && <MeetingActionItems meeting={meeting} />}
          {showText && <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap rounded border border-slate-200 bg-white p-2 font-sans text-xs text-slate-700">{data.transcript}</pre>}
        </div>
      ) : (
        <p className="mt-1 text-slate-500">
          No transcript yet. {canEdit && 'Turn on live captions while recording, or paste or upload one (e.g. from Zoom, Teams or Meet).'}
        </p>
      )}

      {canEdit && meeting.status !== 'cancelled' && (adding || !data.transcript) && (
        adding ? (
          <form onSubmit={save} className="mt-2 space-y-2 border-t border-slate-200 pt-2">
            <label className="block">Transcript
              <textarea value={form.text} onChange={(e) => setForm((f) => ({ ...f, text: e.target.value, source: f.source === 'upload' ? 'upload' : 'pasted' }))}
                rows={6} className={inputClass} placeholder="Paste the conversation here" required />
            </label>
            <label className="text-blue-600 cursor-pointer hover:underline">
              or choose a .txt / .vtt / .srt file
              <input type="file" accept=".txt,.vtt,.srt,text/plain,text/vtt" className="sr-only" onChange={(e) => { readFile(e.target.files?.[0]); e.target.value = ''; }} />
            </label>
            <div className="grid gap-2 sm:grid-cols-2">
              <label>Who agreed to the conversation being kept
                <input value={form.givenBy} onChange={(e) => setForm((f) => ({ ...f, givenBy: e.target.value }))} maxLength={200} className={inputClass} required />
              </label>
              <label>How they agreed
                <select value={form.method} onChange={(e) => setForm((f) => ({ ...f, method: e.target.value }))} className={inputClass}>
                  {METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              </label>
            </div>
            <label className="flex items-start gap-2">
              <input type="checkbox" className="mt-0.5" checked={form.confirmed} onChange={(e) => setForm((f) => ({ ...f, confirmed: e.target.checked }))} />
              Everyone in the meeting agreed to it being recorded or transcribed
            </label>
            <div className="flex gap-2">
              <Button size="sm" type="submit" disabled={busy || !form.confirmed || !form.givenBy.trim() || !form.text.trim()}>Save transcript</Button>
              <Button size="sm" type="button" variant="outline" onClick={() => setAdding(false)}>Cancel</Button>
            </div>
          </form>
        ) : (
          <button type="button" className="mt-1 text-blue-600 hover:underline" onClick={() => setAdding(true)}>Paste or upload a transcript</button>
        )
      )}
    </div>
  );
};

export default MeetingConversation;
