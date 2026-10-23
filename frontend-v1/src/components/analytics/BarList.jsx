import { formatMoney } from '../../core/utils/reportPeriod';

/** A titled list of rows with a proportional bar ({ key, label, count, value }). */
const BarList = ({ title, rows = [], measure = 'value', empty = 'Nothing in this period.' }) => {
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
              <div className="flex justify-between gap-3">
                <span className="text-slate-700 truncate">{r.label}</span>
                <span className="text-slate-900 font-medium whitespace-nowrap">
                  {measure === 'value' ? `${formatMoney(r.value)} · ${r.count}` : r.count}
                </span>
              </div>
              <div className="h-1.5 bg-slate-100 rounded mt-1">
                <div
                  className="h-1.5 bg-violet-500 rounded"
                  style={{ width: `${max ? Math.max(2, (Number(r[measure]) / max) * 100) : 0}%` }}
                />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default BarList;
