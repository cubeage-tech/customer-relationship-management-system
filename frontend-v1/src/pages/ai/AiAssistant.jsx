import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { analyseSentiment, askAi, getAiStatus, getLeadScores } from '../../core/services/ai.service';
import ForecastPanel from '../../components/ai/ForecastPanel';

const RECORD_LINKS = { customer: '/customers', lead: '/leads', opportunity: '/opportunities', ticket: '/service-tickets', campaign: '/campaigns' };
const card = 'bg-white border border-slate-200 rounded-lg p-5';

/** FR-AI-4: ask the CRM in plain language. */
const Chat = ({ available }) => {
  const [question, setQuestion] = useState('');
  const [history, setHistory] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const ask = async (e) => {
    e.preventDefault();
    if (!question.trim()) return;
    setBusy(true);
    setError('');
    try {
      // FR-AI-4.1: the last few questions, so "and last quarter?" is understood.
      const answer = await askAi(question.trim(), history.slice(0, 5).map((a) => a.question));
      setHistory((h) => [answer, ...h]);
      setQuestion('');
    } catch (err) {
      setError(apiErrorMessage(err, 'The assistant could not answer.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={card}>
      <h2 className="font-semibold text-slate-900">Ask the CRM</h2>
      <p className="text-sm text-slate-500 mt-1">e.g. "Show customers who haven't purchased in 6 months", "Which of my leads are hot?" or "What's the forecast for next quarter?" — follow-up questions are understood.</p>
      {!available && <p className="text-sm text-amber-700 mt-3">The AI assistant isn't configured for this CRM yet (an administrator sets the AI provider key).</p>}
      <form onSubmit={ask} className="flex flex-wrap gap-2 mt-3">
        <input
          value={question} onChange={(e) => setQuestion(e.target.value)} maxLength={1000} required
          placeholder="Ask a question about your customers, leads, deals or tickets"
          className="flex-1 min-w-[16rem] border border-slate-300 rounded px-3 py-2 text-sm"
        />
        <Button type="submit" disabled={busy || !available}>{busy ? 'Thinking…' : 'Ask'}</Button>
      </form>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
      <div className="space-y-4 mt-4">
        {history.map((a, i) => (
          <div key={`${a.question}-${i}`} className="border-t border-slate-100 pt-3">
            <p className="text-xs text-slate-400">{a.question}</p>
            <p className="text-sm text-slate-900 mt-1">{a.answer}</p>
            {a.recommendedAction && <p className="text-sm text-primary mt-1">Suggested: {a.recommendedAction}</p>}
            {a.rows.length > 0 && (
              <ul className="mt-2 text-sm list-disc pl-5 text-slate-700">
                {a.rows.map((r) => (
                  <li key={`${r.type}-${r.id ?? r.label}`}>
                    {r.id && RECORD_LINKS[r.type]
                      ? <Link to={`${RECORD_LINKS[r.type]}/${r.id}`} className="hover:underline">{r.label}</Link>
                      : r.label}
                    <span className="text-slate-400"> — {r.detail}</span>
                  </li>
                ))}
              </ul>
            )}
            {a.matchCount > a.rows.length && <p className="text-xs text-slate-400 mt-1">Showing {a.rows.length} of {a.matchCount}.</p>}
            {a.viewAllLink && <Link to={a.viewAllLink} className="mt-1 inline-block text-xs font-medium text-primary hover:underline">View all in the CRM →</Link>}
          </div>
        ))}
      </div>
    </div>
  );
};

/** FR-AI-1: my leads by score. */
const LeadScores = () => {
  const [scores, setScores] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    getLeadScores().then((s) => setScores(s ?? [])).catch((err) => setError(apiErrorMessage(err, 'Could not score leads.')));
  }, []);
  return (
    <div className={card}>
      <h2 className="font-semibold text-slate-900">Lead scores</h2>
      <p className="text-sm text-slate-500 mt-1">From how similar leads converted, recent engagement and pipeline stage.</p>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
      {scores && scores.length === 0 && <p className="text-sm text-slate-400 mt-3">No leads to score.</p>}
      <ul className="mt-3 divide-y divide-slate-100">
        {(scores ?? []).slice(0, 15).map((s) => (
          <li key={s.leadId} className="py-2 flex items-center justify-between gap-3 text-sm">
            <Link to={`/leads/${s.leadId}`} className="min-w-0 hover:underline">
              <span className="font-medium text-slate-900">{s.leadName}</span>
              <span className="text-slate-400"> · {s.companyName}</span>
            </Link>
            <span title={s.factors.map((f) => f.detail).join('\n')}
              className={`shrink-0 px-2 py-0.5 rounded-full text-xs font-semibold ${s.label === 'High' ? 'bg-green-100 text-green-800'
                : s.label === 'Medium' ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'}`}>
              {s.score}% · {s.label}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
};

/** FR-AI-6: check a customer message. */
const SentimentCheck = ({ available }) => {
  const [text, setText] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const check = async (e) => {
    e.preventDefault();
    setError('');
    try {
      setResult(await analyseSentiment({ text }));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not analyse the message.'));
    }
  };
  return (
    <div className={card}>
      <h2 className="font-semibold text-slate-900">Customer sentiment</h2>
      <form onSubmit={check} className="mt-3 space-y-2">
        <textarea value={text} onChange={(e) => setText(e.target.value)} required maxLength={5000} rows={3}
          placeholder="Paste an email, review or chat message from a customer" className="w-full border border-slate-300 rounded px-3 py-2 text-sm" />
        <Button type="submit" size="sm" disabled={!available}>Analyse</Button>
      </form>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
      {result && (
        <p className="text-sm mt-3">
          <span className="font-semibold capitalize">{result.sentiment}</span> ({result.score}) · churn risk{' '}
          <span className={result.churnRisk === 'high' ? 'text-red-600 font-semibold' : ''}>{result.churnRisk}</span>
          <span className="block text-slate-500">{result.reason}</span>
        </p>
      )}
    </div>
  );
};

/**
 * UI-1 / API-G11: the AI Assistant — chat with the CRM, plus insights (lead scores, revenue
 * forecast, sentiment). Messages can be drafted from a customer's or lead's communication timeline.
 */
const AiAssistant = () => {
  const { can } = usePermissions();
  const [available, setAvailable] = useState(true);
  useEffect(() => {
    getAiStatus().then((s) => setAvailable(Boolean(s?.llmConfigured))).catch(() => setAvailable(false));
  }, []);
  const canLeads = can(PERMISSIONS.LEADS_VIEW);
  const canForecast = can(PERMISSIONS.OPPORTUNITIES_VIEW);

  return (
    <section>
      <PageHeader title="AI Assistant" subtitle="Ask questions about your CRM and see lead scores, forecasts and sentiment." />
      <div className="grid gap-6 lg:grid-cols-2">
        <div className="lg:col-span-2"><Chat available={available} /></div>
        {canForecast && <div className="lg:col-span-2"><ForecastPanel /></div>}
        {canLeads && <LeadScores />}
        <SentimentCheck available={available} />
      </div>
    </section>
  );
};

export default AiAssistant;
