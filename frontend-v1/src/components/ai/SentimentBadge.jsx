const STYLE = {
  positive: 'bg-emerald-50 text-emerald-700',
  neutral: 'bg-slate-100 text-slate-600',
  negative: 'bg-red-50 text-red-700',
};

const LABEL = { positive: 'Positive', neutral: 'Neutral', negative: 'Negative' };

/**
 * FR-AI-6.1: the AI's reading of a customer message or review — positive / neutral / negative, with
 * the reason (and churn risk when it is high) on hover and for screen readers.
 */
const SentimentBadge = ({ sentiment }) => {
  if (!sentiment) return null;
  const risk = sentiment.churnRisk === 'high' ? ' · churn risk high' : '';
  const detail = `AI sentiment ${LABEL[sentiment.sentiment] || sentiment.sentiment} (${sentiment.score})${risk}${sentiment.reason ? ` — ${sentiment.reason}` : ''}`;
  return (
    <span title={detail} aria-label={detail}
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${STYLE[sentiment.sentiment] || STYLE.neutral}`}>
      {LABEL[sentiment.sentiment] || sentiment.sentiment}{risk && ' · at risk'}
    </span>
  );
};

export default SentimentBadge;
