/** Summary tile used on the CRM role dashboards. */
const StatCard = ({ label, value, hint, onClick, isActive }) => {
  const content = (
    <>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="text-2xl font-bold text-slate-900 mt-1">{value}</p>
      {hint && <p className="text-xs text-slate-400 mt-1">{hint}</p>}
    </>
  );

  const className = `w-full rounded-lg border p-5 text-left ${
    isActive
      ? 'border-violet-400 bg-violet-50 ring-1 ring-violet-200'
      : 'border-slate-200 bg-white'
  } ${onClick ? 'cursor-pointer transition-colors hover:border-violet-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-400' : ''}`;

  return onClick ? (
    <button type="button" className={className} onClick={onClick} aria-pressed={isActive}>
      {content}
    </button>
  ) : (
    <div className={className}>{content}</div>
  );
};

export default StatCard;
