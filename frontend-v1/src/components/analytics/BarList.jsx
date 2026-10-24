import { formatMoney } from '../../core/utils/reportPeriod';

/**
 * A titled list of rows with a proportional bar ({ key, label, count, value }). With onSelect,
 * each row is a button that drills down into it (UI-8).
 */
const BarList = ({ title, rows = [], measure = 'value', empty = 'Nothing in this period.', onSelect, selectHint }) => {
  const max = Math.max(0, ...rows.map((r) => Number(r[measure]) || 0));

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <h3 className="font-semibold text-slate-900 mb-3">{title}</h3>
      {rows.length === 0 ? (
        <p className="text-sm text-slate-400">{empty}</p>
      ) : (
        <ul className="space-y-2">
          {rows.map((r) => (
            <li key={r.key ?? r.label} className="text-sm">
              <Row onSelect={onSelect && r.key && r.key !== 'null' ? () => onSelect(r) : null} hint={selectHint}>
              <div className="flex justify-between gap-3">
                <span className="text-slate-700 truncate">{r.label}</span>
                <span className="text-slate-900 font-medium whitespace-nowrap">
                  {measure === 'value' ? `${formatMoney(r.value)} · ${r.count}` : r.count}
                </span>
              </div>
              <div className="h-1.5 bg-slate-100 rounded mt-1">
                <div
                  className="h-1.5 bg-primary rounded"
                  style={{ width: `${max ? Math.max(2, (Number(r[measure]) / max) * 100) : 0}%` }}
                />
              </div>
              </Row>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

/** A drill-down row is a button; otherwise plain content. */
const Row = ({ onSelect, hint, children }) => (onSelect ? (
  <button type="button" onClick={onSelect} title={hint}
    className="block w-full text-left rounded -mx-1 px-1 py-0.5 hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
    {children}
  </button>
) : children);

export default BarList;
