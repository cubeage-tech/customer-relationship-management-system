const STYLES = {
  High: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  Medium: 'bg-amber-50 text-amber-700 ring-amber-200',
  Low: 'bg-slate-100 text-slate-600 ring-slate-200',
};

/**
 * FR-AI-1.2 / FR-AI-2.2: an AI score (0–100) or close chance (%) with its High/Medium/Low label.
 * Shows "—" until the record has been scored.
 */
const ScoreBadge = ({ value, label, suffix = '', title }) => {
  if (value == null) return <span className="text-xs text-slate-400" title="Not scored yet">—</span>;
  return (
    <span title={title} className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${STYLES[label] || STYLES.Low}`}>
      {value}{suffix}
      {label && <span className="font-medium">{label}</span>}
    </span>
  );
};

export default ScoreBadge;
