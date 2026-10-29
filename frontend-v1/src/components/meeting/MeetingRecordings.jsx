import { useEffect, useRef, useState } from 'react';
import { Mic, Square } from 'lucide-react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import {
  deleteMeetingRecording, getMeetingRecordingAudio, listMeetingRecordings, saveMeetingTranscript, uploadMeetingRecording,
} from '../../core/services/meeting.service';

const METHODS = [
  { value: 'verbal', label: 'Said yes at the start (verbal)' },
  { value: 'written', label: 'In writing' },
  { value: 'email', label: 'By email' },
];
const EARLY_MS = 15 * 60 * 1000;
const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

// FR-AI-7.2: the browser's own speech service for live captions (Chrome/Edge), when there is one.
const SpeechRecognition = typeof window !== 'undefined' ? window.SpeechRecognition || window.webkitSpeechRecognition : undefined;

const clock = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

/**
 * FR-AI-7.1: a meeting's audio recordings. Recording (in the browser, or uploading a file) only opens
 * once the user records who agreed to be recorded and how, and confirms everyone consented; each
 * recording shows that consent. Playing is audited; a recording is deleted when consent is withdrawn.
 * FR-AI-7.2: optional live captions while recording are saved as the meeting's transcript.
 */
const MeetingRecordings = ({ meeting, canRecord, onTranscript }) => {
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [consent, setConsent] = useState({ givenBy: '', method: 'verbal', note: '', confirmed: false });
  const [recording, setRecording] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [busy, setBusy] = useState(false);
  const [players, setPlayers] = useState({}); // recording id → object URL
  const recorder = useRef(null);
  const startedAt = useRef(0);
  const [captions, setCaptions] = useState(false);
  const speech = useRef(null);
  const heard = useRef([]);
  const speechDone = useRef(Promise.resolve());
  const endSpeech = useRef(null);
  const [openedAt] = useState(() => Date.now()); // when the panel opened: recording opens 15 min before the start

  const refresh = () => listMeetingRecordings(meeting.id).then((d) => setItems(d ?? [])).catch((err) => setError(apiErrorMessage(err, 'Could not load the recordings.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meeting.id]);

  useEffect(() => {
    if (!recording) return undefined;
    const t = setInterval(() => setSeconds(Math.round((Date.now() - startedAt.current) / 1000)), 1000);
    return () => clearInterval(t);
  }, [recording]);

  // Release the microphone and player URLs when the panel closes.
  useEffect(() => () => {
    recorder.current?.stream?.getTracks().forEach((t) => t.stop());
    Object.values(players).forEach((u) => URL.revokeObjectURL(u));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const open = canRecord && meeting.status !== 'cancelled' && new Date(meeting.startsAt).getTime() - openedAt <= EARLY_MS;
  const consentReady = consent.confirmed && consent.givenBy.trim().length > 0;

  const save = async (file, durationSeconds) => {
    setBusy(true);
    setError('');
    try {
      const saved = await uploadMeetingRecording(meeting.id, file, {
        consentConfirmed: consent.confirmed, consentGivenBy: consent.givenBy.trim(), consentMethod: consent.method,
        consentNote: consent.note.trim() || undefined, durationSeconds,
      });
      const text = heard.current.join(' ').trim();
      heard.current = [];
      if (text && saved?.id) {
        await saveMeetingTranscript(meeting.id, { text, source: 'browser', recordingId: saved.id });
        onTranscript?.();
      }
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the recording.'));
    } finally {
      setBusy(false);
    }
  };

  const start = async () => {
    setError('');
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError('This browser cannot record audio — record on another device and upload the file instead.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const chunks = [];
      const rec = new MediaRecorder(stream);
      rec.ondataavailable = (e) => e.data.size > 0 && chunks.push(e.data);
      rec.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        const type = rec.mimeType || 'audio/webm';
        const ext = type.includes('mp4') ? 'm4a' : type.includes('ogg') ? 'ogg' : 'webm';
        const duration = Math.round((Date.now() - startedAt.current) / 1000);
        speechDone.current.then(() => save(new File(chunks, `meeting-${meeting.id}.${ext}`, { type }), duration));
      };
      recorder.current = rec;
      startedAt.current = Date.now();
      setSeconds(0);
      rec.start(1000);
      heard.current = [];
      if (captions && SpeechRecognition) {
        const sr = new SpeechRecognition();
        sr.continuous = true;
        sr.interimResults = false;
        sr.lang = navigator.language || 'en-IN';
        sr.onresult = (e) => {
          for (let i = e.resultIndex; i < e.results.length; i++) {
            if (e.results[i].isFinal) heard.current.push(e.results[i][0].transcript.trim());
          }
        };
        // The service stops after a pause; keep listening while the recording runs.
        sr.onend = () => {
          if (recorder.current?.state === 'recording') {
            try { sr.start(); } catch { /* already listening */ }
          } else {
            endSpeech.current?.();
          }
        };
        sr.start();
        speech.current = sr;
      }
      setRecording(true);
    } catch {
      setError('Microphone access was refused — allow it in the browser to record.');
    }
  };

  const stop = () => {
    // Wait (briefly) for the last captions before saving.
    speechDone.current = speech.current ? new Promise((resolve) => { endSpeech.current = resolve; setTimeout(resolve, 1500); }) : Promise.resolve();
    recorder.current?.stop();
    speech.current?.stop();
    speech.current = null;
    setRecording(false);
  };

  const play = async (r) => {
    try {
      const res = await getMeetingRecordingAudio(meeting.id, r.id);
      setPlayers((p) => ({ ...p, [r.id]: URL.createObjectURL(new Blob([res.data], { type: r.contentType })) }));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load this recording.'));
    }
  };

  const remove = async (r) => {
    const reason = window.prompt('Delete this recording? It cannot be recovered. Reason (e.g. consent withdrawn):', 'Consent withdrawn');
    if (reason === null) return;
    try {
      await deleteMeetingRecording(meeting.id, r.id, reason);
      if (players[r.id]) URL.revokeObjectURL(players[r.id]);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not delete this recording.'));
    }
  };

  return (
    <div className="mt-2 w-full rounded-md bg-slate-50 p-3 text-sm">
      {error && <p className="mb-2 text-red-600">{error}</p>}
      {items.length === 0 ? (
        <p className="text-slate-500">No recordings.</p>
      ) : (
        <ul className="space-y-2">
          {items.map((r) => (
            <li key={r.id} className="rounded border border-slate-200 bg-white p-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-slate-800">
                  {new Date(r.recordedAt).toLocaleString()}{r.durationSeconds != null ? ` · ${clock(r.durationSeconds)}` : ''} · {r.recordedByName || '—'}
                </span>
                <span className="flex gap-3">
                  {!players[r.id] && <button type="button" className="text-blue-600 hover:underline" onClick={() => play(r)}>Play</button>}
                  {canRecord && <button type="button" className="text-red-600 hover:underline" onClick={() => remove(r)}>Delete</button>}
                </span>
              </div>
              <p className="text-xs text-slate-500">
                Consent: {r.consentGivenBy}, {r.consentMethod}, {new Date(r.consentAt).toLocaleString()}{r.consentNote ? ` — ${r.consentNote}` : ''}
              </p>
              {players[r.id] && <audio controls src={players[r.id]} className="mt-1 w-full"><track kind="captions" /></audio>}
            </li>
          ))}
        </ul>
      )}

      {open && (
        <div className="mt-3 space-y-2 border-t border-slate-200 pt-3">
          <p className="font-medium text-slate-800">Record this meeting</p>
          <p className="text-xs text-slate-500">Tell everyone the meeting will be recorded and ask for their agreement before you start.</p>
          <label className="block">Who agreed to be recorded
            <input value={consent.givenBy} onChange={(e) => setConsent((c) => ({ ...c, givenBy: e.target.value }))} maxLength={200}
              className={inputClass} placeholder="e.g. Ravi Kumar (ABC Engineering) and our team" disabled={recording} />
          </label>
          <div className="grid gap-2 sm:grid-cols-2">
            <label>How they agreed
              <select value={consent.method} onChange={(e) => setConsent((c) => ({ ...c, method: e.target.value }))} className={inputClass} disabled={recording}>
                {METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
              </select>
            </label>
            <label>Note (optional)
              <input value={consent.note} onChange={(e) => setConsent((c) => ({ ...c, note: e.target.value }))} maxLength={500} className={inputClass} disabled={recording} />
            </label>
          </div>
          <label className="flex items-start gap-2">
            <input type="checkbox" className="mt-0.5" checked={consent.confirmed} disabled={recording}
              onChange={(e) => setConsent((c) => ({ ...c, confirmed: e.target.checked }))} />
            Everyone in the meeting agreed to it being recorded
          </label>
          {SpeechRecognition && (
            <label className="flex items-start gap-2 text-xs text-slate-600">
              <input type="checkbox" className="mt-0.5" checked={captions} disabled={recording} onChange={(e) => setCaptions(e.target.checked)} />
              Also transcribe live (for the AI summary) with the browser's speech service — in Chrome the audio is sent to Google for this
            </label>
          )}
          <div className="flex flex-wrap items-center gap-3">
            {recording ? (
              <>
                <span className="flex items-center gap-1 font-medium text-red-600" role="status">
                  <span className="size-2 animate-pulse rounded-full bg-red-600" /> Recording {clock(seconds)}
                </span>
                <Button size="sm" onClick={stop} icon={Square}>Stop and save</Button>
              </>
            ) : (
              <>
                <Button size="sm" onClick={start} disabled={!consentReady || busy} icon={Mic}>{busy ? 'Saving…' : 'Start recording'}</Button>
                <label className={`text-blue-600 ${consentReady && !busy ? 'cursor-pointer hover:underline' : 'pointer-events-none opacity-50'}`}>
                  or upload a recording
                  <input type="file" accept="audio/*" className="sr-only" disabled={!consentReady || busy}
                    onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) save(f, undefined); }} />
                </label>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default MeetingRecordings;
